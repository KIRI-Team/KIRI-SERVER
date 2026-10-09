# KIRI Infra

KIRI의 AWS 인프라(Terraform)와 서버의 nginx 설정을 관리합니다.

- 리전: `ap-northeast-2` (서울)
- Terraform: `>= 1.10`
- AWS 계정: `100024219481` (provider의 `allowed_account_ids`로 다른 계정에서는 실행되지 않음)
- dev 주소: `https://kiri-dev.halo-app.co.kr`

배포 흐름, 결과 확인, 롤백은 [docs/deploy.md](../docs/deploy.md)를 참고합니다.

## 목차

- [사전 준비](#사전-준비)
- [자주 쓰는 작업](#자주-쓰는-작업): 서버 접속, DB 접속, 값 조회
- [디렉터리 구조](#디렉터리-구조)
- 환경 구성: [dev](#dev-환경), [nginx](#nginx-https-basic-auth), [앱 환경변수](#앱-환경변수-parameter-store), [이미지 저장소](#이미지-저장소-s3--cloudfront), [EC2 초기 설정](#ec2-초기-설정-user_data), [shared](#shared-환경), [비용 알람](#비용-알람-global)
- [Terraform 작업](#terraform-작업): 실행 방법, tfvars, 운영 규칙
- [새 환경 만들기](#새-환경-만들기-github-actions-배포-설정)
- [주의 사항](#주의-사항)

## 사전 준비

1. Terraform, AWS CLI, [Session Manager 플러그인](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) 설치
2. KIRI 계정의 IAM 사용자 access key 발급
3. AWS CLI 프로필을 **`kiri`라는 이름으로** 등록

   ```bash
   aws configure --profile kiri
   # region: ap-northeast-2, output: json
   ```

4. 등록 확인 (`Account`가 `100024219481`이어야 함)

   ```bash
   aws sts get-caller-identity --profile kiri
   ```

> 코드의 provider와 backend에 `profile = "kiri"`가 고정되어 있으므로, 프로필 이름이 다르면 실행되지 않습니다.
>
> Git Bash에서는 `/kiri/...` 같은 인자가 Windows 경로로 바뀌어 실패하므로 명령 앞에 `MSYS_NO_PATHCONV=1`을 붙입니다.

## 자주 쓰는 작업

### 서버 접속 (SSM Session Manager)

SSH 대신 SSM으로 접속합니다. 키 파일이 필요 없고, IAM 권한으로 접속을 제어합니다. (22번 포트는 닫혀 있음)

```bash
cd infra/terraform/envs/dev
terraform output ec2_instance_id
aws ssm start-session --target <ec2_instance_id> --profile kiri
```

### DB 접속 (포트 포워딩)

RDS는 private subnet에 있어 로컬에서 직접 접속할 수 없습니다.
EC2를 경유해 로컬 `15432` 포트를 RDS `5432`에 연결합니다. (`rds_address`는 `terraform output rds_address`)

```bash
aws ssm start-session --target <ec2_instance_id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters host=<rds_address>,portNumber=5432,localPortNumber=15432 \
  --profile kiri
```

`Waiting for connections...`가 출력되면 터널이 열린 상태입니다. 이 창을 닫으면 연결이 끊기므로 열어 둔 채로 접속합니다.

| DB 클라이언트 항목 | 값 |
|---|---|
| Host / Port | `localhost` / `15432` |
| User / Database | `kiri` / `kiri` |
| Password | 아래 [값 조회](#값-조회-parameter-store) 참고 (`DB_PW`) |
| SSL | `require` (호스트가 `localhost`라 `verify-full`은 실패함) |

### 값 조회 (Parameter Store)

```bash
aws ssm get-parameter --name <이름> --with-decryption --query Parameter.Value --output text --profile kiri
```

| 필요한 값 | `<이름>` |
|---|---|
| DB 비밀번호 | `/kiri/dev/DB_PW` |
| Swagger 계정 (basic auth) | `/kiri/dev/nginx/BASIC_AUTH_USER`, `/kiri/dev/nginx/BASIC_AUTH_PASSWORD` |

전체 목록은 [앱 환경변수](#앱-환경변수-parameter-store)를 참고합니다.

## 디렉터리 구조

```
infra/
├── nginx/              # 서버 nginx 설정 (배포 시 EC2에 자동 반영)
│   ├── compose.yml     # nginx, certbot 컨테이너 (dev/prod 공용)
│   └── dev.conf        # dev 서버 설정
└── terraform/
    ├── bootstrap/      # tfstate 저장용 S3 버킷
    ├── global/         # 계정 공통 설정 (Budgets, Cost Anomaly Detection)
    ├── envs/
    │   ├── shared/     # 환경보다 먼저 만드는 배포 리소스 (GitHub OIDC Provider, 환경별 ECR)
    │   ├── dev/        # dev 환경 리소스
    │   └── prod/       # prod 환경 리소스
    └── modules/        # 환경 간 공통 모듈 (envs/{env}/main.tf에서 호출)
        ├── network/        # VPC, Subnet, IGW, S3 Gateway Endpoint, Security Group
        ├── ec2/            # EC2, IAM Role, Elastic IP, CloudWatch Log Group
        ├── rds/            # RDS PostgreSQL, 접속 정보 Parameter Store 저장
        ├── images/         # 이미지 S3 버킷, CloudFront, Signed URL 키 그룹
        └── github_deploy/  # GitHub Actions 배포용 IAM Role (OIDC)
```

Terraform의 각 디렉터리는 독립된 루트이며, state는 S3 버킷 `kiri-tfstate-100024219481`에 따로 저장됩니다. (`use_lockfile`로 동시 apply 방지)

| 디렉터리 | state key |
|---|---|
| `terraform/bootstrap` | `bootstrap/terraform.tfstate` |
| `terraform/global` | `global/terraform.tfstate` |
| `terraform/envs/shared` | `shared/terraform.tfstate` |
| `terraform/envs/dev` | `dev/terraform.tfstate` |
| `terraform/envs/prod` | `prod/terraform.tfstate` |

## dev 환경

| 리소스 | 사양 |
|---|---|
| VPC | `10.0.0.0/16`, AZ 2개 (`2a`, `2c`) |
| Subnet | Public `10.0.0.0/24`, `10.0.1.0/24` / Private `10.0.10.0/24`, `10.0.11.0/24` |
| NAT Gateway | 없음 (비용 절감, private subnet은 VPC 내부 통신만 가능) |
| EC2 | `t4g.small` (arm64), Amazon Linux 2023, gp3 20GB, Elastic IP, CPU 크레딧 `standard` |
| RDS | PostgreSQL 18, `db.t4g.micro`, Single-AZ, gp3 20GB, private subnet |
| CloudWatch Logs | `/kiri/dev/app` (앱, nginx), 보관 7일 |
| S3 (이미지) | `kiri-dev-images-100024219481`, 퍼블릭 차단, SSE-S3 |
| CloudFront | `terraform output cdn_domain`, PriceClass_200 (한국 엣지 포함) |
| 도메인 | `kiri-dev.halo-app.co.kr` (가비아, A 레코드 → EC2 Elastic IP) |

**보안 그룹**

| 대상 | 허용 |
|---|---|
| EC2 | 80, 443만 허용. 22(SSH), 8080(앱)은 닫힘 |
| RDS | 5432, EC2 보안 그룹에서 오는 요청만 |

- 앱 컨테이너는 EC2 내부(`127.0.0.1:8080`)에만 열려 있고, 외부 요청은 모두 nginx를 거칩니다.
- nginx를 거치지 않고 앱에 직접 붙어야 하면(디버깅 등) 보안 그룹을 열지 않고 SSM 포트 포워딩을 씁니다. 로컬 `18080` → 앱 `8080`:

  ```bash
  aws ssm start-session --target <ec2_instance_id> \
    --document-name AWS-StartPortForwardingSession \
    --parameters portNumber=8080,localPortNumber=18080 \
    --profile kiri
  ```
- 야간·주말에도 작업이 있어 자동 중지 스케줄은 적용하지 않았습니다.

> EC2가 arm64이므로 Docker 이미지는 `linux/arm64`로 빌드해야 합니다.

## nginx (HTTPS, basic auth)

EC2에서 nginx가 컨테이너(`kiri-nginx`)로 실행되며, 외부 요청을 받아 앱으로 전달합니다.

```
인터넷 ──80/443──▶ nginx (kiri-nginx) ──127.0.0.1:8080──▶ 앱 (kiri-server)
```

| 요청 | 처리 |
|---|---|
| `http://...` | `https://`로 301 이동 (인증서 갱신용 `/.well-known/acme-challenge/`만 예외) |
| IP로 직접 접속, 다른 도메인 | 연결 끊기 (80: `444`, 443: 핸드셰이크 거절) |
| `/api/**`, `/actuator/health` | 그대로 앱으로 전달 (API 인증은 앱의 JWT) |
| `/actuator/info`, Swagger 등 나머지 | **basic auth** 필요 |
| 그 외 `/actuator/**` | `404` |

### 설정 반영

`infra/nginx/`를 수정해서 develop에 머지하면, 배포 스크립트(`scripts/deploy/deploy.sh`의 `apply_nginx`)가 EC2의 `/opt/kiri/nginx`에 반영합니다.

- 바뀐 게 없으면 아무것도 하지 않습니다.
- 반영 전에 임시 컨테이너로 `nginx -t` 검사를 하고, 실패하면 기존 설정을 유지한 채 배포를 실패 처리합니다.
- conf만 바뀌면 `nginx -s reload`(접속 끊김 없음), `compose.yml`이 바뀌면 컨테이너를 새로 만듭니다.
- `{env}.conf`가 없는 환경(현재 prod)은 nginx 반영을 건너뜁니다.

로그는 CloudWatch `/kiri/dev/app`의 `nginx` 스트림에서 봅니다.

### 인증서 (Let's Encrypt)

발급과 갱신 모두 자동입니다.

- **발급:** 배포 때 conf의 `ssl_certificate`에 적힌 인증서가 없으면 certbot으로 발급합니다. (nginx가 없으면 standalone, 있으면 webroot 방식)
- **갱신:** 배포 스크립트가 설치한 systemd timer(`certbot-renew.timer`)가 하루 2회 `certbot renew` 후 nginx를 reload합니다.

도메인의 DNS가 이 서버를 가리켜야 발급됩니다. 같은 도메인으로 확인에 실패하면 Let's Encrypt가 1시간에 5번까지만 허용하므로, 실패하면 원인(DNS, 80 포트)을 먼저 고친 뒤 다시 배포합니다.

갱신 상태 확인 (EC2):

```bash
systemctl list-timers certbot-renew.timer
cd /opt/kiri/nginx && sudo docker compose run --rm -T certbot renew --webroot -w /var/www/certbot --dry-run
```

> Let's Encrypt는 만료 알림 메일을 보내지 않습니다. 외부 모니터링(UptimeRobot 등)으로 인증서 만료일을 확인합니다.
> HSTS가 1년으로 걸려 있어, 인증서에 문제가 생기면 브라우저에서 우회 접속이 불가능합니다.

### basic auth 계정

Terraform(`terraform/envs/dev/nginx.tf`)이 Parameter Store `/kiri/dev/nginx/`에 만들고, 배포 때 `.htpasswd`로 변환됩니다. 조회는 [값 조회](#값-조회-parameter-store)를 참고합니다.

비밀번호를 바꾸려면 새로 생성한 뒤 재배포합니다.

```bash
terraform apply -replace=random_password.nginx_basic_auth
```

### 공유 도메인 주의

`halo-app.co.kr`은 HALO 팀과 함께 쓰는 도메인입니다.

- 가비아의 기존 레코드(`@`, `www`, `api`, `dev`)는 수정하지 않습니다.
- HSTS에 `includeSubDomains`, `preload`를 넣지 않습니다. (HALO 서브도메인까지 영향)

## 앱 환경변수 (Parameter Store)

`/kiri/{env}/` **바로 아래** 값이 배포 시 이름 그대로 앱 환경변수로 주입됩니다. 하위 경로(예: `/kiri/dev/nginx/`)는 주입되지 않습니다.

| 이름 | 값 | 생성 |
|---|---|---|
| `/kiri/dev/DB_URL` | `jdbc:postgresql://<rds_endpoint>/kiri` | Terraform |
| `/kiri/dev/DB_USER` | DB 사용자 이름 | Terraform |
| `/kiri/dev/DB_PW` | DB 비밀번호 (`random_password`) | Terraform |
| `/kiri/dev/S3_IMAGE_BUCKET` | 이미지 버킷 이름 | Terraform |
| `/kiri/dev/CDN_BASE_URL` | `https://<cdn_domain>` | Terraform |
| `/kiri/dev/CLOUDFRONT_KEY_PAIR_ID` | Signed URL `Key-Pair-Id` | Terraform |
| `/kiri/dev/CLOUDFRONT_PRIVATE_KEY` | Signed URL 서명용 개인키 | 수동 ([Signed URL 키](#signed-url-키) 참고) |
| `/kiri/dev/JWT_SECRET` | JWT 서명 키 | 수동 |

- EC2의 IAM Role은 `/kiri/dev/*` 경로만 읽을 수 있습니다.
- 새 값을 추가하는 방법은 [docs/deploy.md](../docs/deploy.md#환경변수-추가)를 참고합니다.

## 이미지 저장소 (S3 + CloudFront)

이미지는 S3에 저장하고 CloudFront로만 조회합니다. S3 URL로 직접 접근하면 403입니다.

### key 구조와 접근 규칙

| 경로 | 용도 | 조회 |
|---|---|---|
| `public/{uuid}.{ext}` | 공용 이미지 | 공개 |
| `pending/user/{memberId}/{uuid}.{ext}` | 업로드 직후 임시 파일, **1일 후 자동 삭제** | 불가 |
| `user/original/{memberId}/{uuid}.{ext}` | 확정된 사용자 이미지 | Signed URL 필요 |
| `user/resized/...` | 리사이징 결과 (예정) | Signed URL 필요 |

- `public/*` 외의 모든 경로는 Signed URL이 필요합니다. 새 경로를 만들어도 기본은 비공개입니다.
- 이미지 key는 덮어쓰지 않고 항상 새 UUID로 만듭니다. CloudFront와 앱에서 1년 캐시하므로, 같은 key의 내용을 바꾸면 반영되지 않습니다.
- 업로드 시 `Cache-Control: public, max-age=31536000, immutable`을 붙입니다. 서버는 presigned PUT URL에 이 헤더를 포함해 서명합니다.

공용 이미지(배너, 기본 이미지 등)는 업로드 API가 없으므로 직접 올립니다. Content-Type과 Cache-Control을 빠뜨리지 않습니다.

```bash
aws s3 cp banner.jpg s3://kiri-dev-images-100024219481/public/<uuid>.jpg \
  --content-type image/jpeg \
  --cache-control "public, max-age=31536000, immutable" \
  --profile kiri
```

### Signed URL 키

- 공개키: `terraform/envs/{env}/keys/cloudfront-public.pem` (Terraform이 CloudFront에 등록)
- 개인키: Parameter Store `CLOUDFRONT_PRIVATE_KEY`에 수동 등록, git에 올리지 않음
  - **한 줄 base64(PKCS#8 DER)** 로 저장합니다. 여러 줄 PEM은 배포 시 환경변수로 주입되지 않습니다.
- 환경마다 키를 따로 만듭니다.

> **키를 교체하면 이미 발급된 Signed URL은 모두 무효가 됩니다.** 공개키 교체 apply와 개인키 갱신, 앱 재배포를 함께 진행합니다.

## EC2 초기 설정 (user_data)

EC2가 처음 부팅될 때 `terraform/modules/ec2/user_data.sh`가 실행되어 다음을 설치합니다.

- swap 2GB
- Docker, Docker Compose 플러그인
- psql (RDS 접속 확인용)

nginx, certbot은 서버에 설치하지 않고 첫 배포 때 컨테이너로 실행됩니다. 인증서 발급과 갱신 timer 등록도 배포 스크립트가 합니다.

실행 결과는 서버에서 확인합니다.

```bash
sudo cloud-init status --wait
sudo tail -n 40 /var/log/cloud-init-output.log
```

> **user_data는 최초 부팅 시 1회만 실행됩니다.**
> 스크립트를 수정해도 기존 인스턴스에는 반영되지 않습니다. (`ignore_changes = [user_data]`로 재부팅·재생성도 일어나지 않음)
> 기존 서버에 반영하려면 SSM으로 접속해서 직접 실행합니다.
>
> `user_data.sh`는 줄바꿈이 **LF**여야 합니다. (CRLF면 스크립트가 실행되지 않음)

## shared 환경

환경 인프라보다 먼저 있어야 하거나, 환경을 지워도 남아야 하는 배포 리소스입니다. `terraform/envs/dev`, `terraform/envs/prod`보다 **먼저 apply**합니다.

| 리소스 | 내용 |
|---|---|
| GitHub OIDC Provider | GitHub Actions가 Access Key 없이 AWS에 인증하기 위한 연결. 계정당 1개라 dev, prod의 배포 Role이 함께 사용 |
| ECR `kiri-server-dev`, `kiri-server-prod` | 앱 이미지 저장소. **환경별로 따로** 있음 (dev 최신 5개, prod 최신 10개 보관). 환경 인프라를 지웠다가 다시 만들어도 이미지가 남도록 여기에 둠 |

## 비용 알람 (global)

| 종류 | 알림 조건 |
|---|---|
| AWS Budgets (`monthly_budget_usd`) | 실제 사용액 50%, 100% 초과 / 예상 사용액 100% 초과 (크레딧 제외) |
| Cost Anomaly Detection | 이상 비용 5 USD 이상, 하루 1회 (AWS 기본 모니터 `Default-Services-Monitor`를 import해서 사용) |

> 이메일 수신자는 AWS가 보내는 확인 메일을 승인해야 알림을 받습니다.
> `alert_emails`에 주소를 추가한 뒤에는 콘솔에서 Active 상태인지 확인합니다.

## Terraform 작업

### 실행 방법

작업할 디렉터리로 이동해서 실행합니다.

```bash
cd infra/terraform/envs/dev
terraform init      # 처음 한 번, 또는 provider/backend 변경 시
terraform fmt       # 코드 정렬
terraform plan      # 변경 사항 미리 보기
terraform apply     # 실제 반영 (yes 입력 필요)
```

apply 전에는 plan 결과의 `destroy` 개수와 `-/+`(삭제 후 재생성) 항목을 반드시 확인합니다.

일부 리소스만 적용할 때는 `-target`을 씁니다. PowerShell에서는 `.`에서 인자가 잘리므로 작은따옴표로 감쌉니다.

```powershell
terraform apply '-target=aws_ssm_parameter.nginx_basic_auth_user'
```

### 변수 파일 (tfvars)

이메일 등 민감한 값은 `terraform.tfvars`로 주입하며, git에 올리지 않습니다.

```bash
cp terraform.tfvars.example terraform.tfvars
```

`terraform/global/terraform.tfvars`는 AWS에 적용된 값을 조회해서 채웁니다. (Budgets, Cost Explorer는 리전과 무관한 서비스라 `us-east-1`로 조회)

```bash
# monthly_budget_usd
aws budgets describe-budget --account-id 100024219481 --budget-name kiri-monthly-budget \
  --query "Budget.BudgetLimit.Amount" --output text --profile kiri --region us-east-1

# alert_emails
aws ce get-anomaly-subscriptions \
  --query "AnomalySubscriptions[?SubscriptionName=='kiri-anomaly-alert'].Subscribers[].Address" \
  --output text --profile kiri --region us-east-1
```

채운 뒤 `terraform plan`이 `No changes`인지 확인합니다. 변경이 나오면 조회한 값과 tfvars가 다른 것입니다.

### 운영 규칙

- **apply는 로컬에서 실행합니다.** PR 머지로 자동 반영되지 않습니다.
- **레포 코드와 실제 AWS 상태를 항상 일치시킵니다.**
  - 코드를 수정했다면 apply 후 PR을 올리고, 리뷰로 코드가 바뀌면 다시 apply합니다.
  - Terraform으로 관리하는 리소스는 콘솔에서 직접 수정하지 않습니다. (`ManagedBy = terraform` 태그로 구분)
  - 환경별 state는 브랜치와 상관없이 하나입니다. 다른 브랜치에서 apply한 변경이 현재 브랜치 코드에 없으면 plan에 되돌리는 변경으로 나타나므로, apply한 브랜치는 빨리 머지합니다.
- 커밋하지 않는 파일: `.terraform/`, `*.tfstate`, `*.tfvars`
- `.terraform.lock.hcl`은 provider 버전 고정용이므로 커밋합니다.

### 네이밍 / 태그

- 리소스 이름: `kiri-{env}-*` (예: `kiri-dev-api`)
- 공통 태그는 provider의 `default_tags`로 자동 부여됩니다.

| 태그 | 값 |
|---|---|
| `Project` | `kiri` |
| `Environment` | `bootstrap` / `global` / `shared` / `dev` / `prod` |
| `ManagedBy` | `terraform` |

## 새 환경 만들기 (GitHub Actions 배포 설정)

GitHub Actions는 `terraform/modules/github_deploy`로 만든 환경별 Role(dev: `kiri-dev-github-deploy`)로 배포합니다.
이 Role은 ECR push와 해당 환경 EC2 1대에 대한 SSM 명령 실행만 할 수 있습니다.

1. `terraform/envs/shared` → `terraform/envs/{env}` 순서로 apply
2. GitHub › Settings › Environments에서 `{env}` 생성
   - Deployment branches and tags: **Selected branches and tags** → dev는 `develop`, prod는 `main`
   - Environment variables: `AWS_ROLE_ARN` = `terraform output github_deploy_role_arn`
3. Parameter Store에 Terraform이 만들지 않는 값 등록 (예: `JWT_SECRET`, [앱 환경변수](#앱-환경변수-parameter-store) 참고)
4. nginx를 쓰려면 `infra/nginx/{env}.conf` 작성, 도메인 DNS 등록, `/kiri/{env}/nginx/` basic auth 계정 생성
   - 배포 스크립트는 `{env}.conf`가 있으면 이 계정을 반드시 읽으므로, 계정 없이 conf만 추가하면 배포가 실패합니다.

> **브랜치 제한은 AWS가 아니라 GitHub Environment 설정이 담당합니다.** Role은 "해당 Environment에서 실행된 job"인지만 확인합니다.
> Environment를 만들기 전에 워크플로가 실행되면 GitHub이 제한 없는 Environment를 자동 생성하므로, 반드시 먼저 만듭니다.

## 주의 사항

- **AMI 자동 업데이트 무시**: 최신 AL2023 AMI를 조회하지만 `ignore_changes = [ami]`로 설정되어, 새 AMI가 나와도 인스턴스가 재생성되지 않습니다.
- **RDS 생성/삭제**: 10분 정도 걸립니다.
- **dev RDS는 삭제 보호가 꺼져 있고, 삭제 시 최종 스냅샷을 남기지 않습니다.** prod에서는 `deletion_protection = true`, `skip_final_snapshot = false`로 설정합니다.
