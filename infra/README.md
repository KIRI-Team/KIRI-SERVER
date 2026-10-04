# KIRI Infra

Terraform으로 KIRI의 AWS 인프라를 관리합니다.

- 리전: `ap-northeast-2` (서울)
- Terraform: `>= 1.10`
- AWS 계정: `100024219481` (provider의 `allowed_account_ids`로 다른 계정에서는 실행되지 않음)

## 디렉터리 구조

```
infra/
├── bootstrap/   # tfstate 저장용 S3 버킷
├── global/      # 계정 공통 설정 (Budgets, Cost Anomaly Detection 등)
├── envs/
│   ├── dev/     # dev 환경 리소스
│   └── prod/    # prod 환경 리소스
└── modules/     # 환경 간 공통으로 쓰는 모듈
    ├── network/ # VPC, Subnet, IGW, S3 Gateway Endpoint, Security Group
    ├── ec2/     # EC2, IAM Role, Elastic IP, CloudWatch Log Group
    └── rds/     # RDS PostgreSQL, 접속 정보 Parameter Store 저장
```

`modules/`는 단독으로 실행하지 않고, `envs/{env}/main.tf`에서 호출해서 사용합니다.
환경별 값(인스턴스 타입, 삭제 보호 등)은 `envs/{env}/main.tf`의 모듈 호출부에서 주입합니다.

각 디렉터리는 독립된 Terraform 루트이며, state도 따로 관리됩니다.

| 디렉터리 | state key |
|---|---|
| `bootstrap` | `bootstrap/terraform.tfstate` |
| `global` | `global/terraform.tfstate` |
| `envs/dev` | `dev/terraform.tfstate` |
| `envs/prod` | `prod/terraform.tfstate` |

state는 모두 S3 버킷 `kiri-tfstate-100024219481`에 저장되며, `use_lockfile`로 동시 apply를 막습니다.

## 사전 준비

1. Terraform, AWS CLI 설치
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

## 사용 방법

작업할 디렉터리로 이동해서 실행합니다.

```bash
cd infra/envs/dev
terraform init      # 처음 한 번, 또는 provider/backend 변경 시
terraform fmt       # 코드 정렬
terraform plan      # 변경 사항 미리 보기
terraform apply     # 실제 반영 (yes 입력 필요)
```

apply 전에는 plan 결과의 `destroy` 개수와 `-/+`(삭제 후 재생성) 항목을 반드시 확인합니다.

## 변수 파일 (tfvars)

이메일 등 민감한 값은 `terraform.tfvars`로 주입하며, git에 올리지 않습니다.

```bash
cp terraform.tfvars.example terraform.tfvars
# 값 채우기
```

`global/terraform.tfvars`의 실제 값은 인프라 담당자에게 받습니다.

## 운영 규칙

- **apply는 로컬에서 실행합니다.** PR 머지로 자동 반영되지 않습니다.
- **레포 코드와 실제 AWS 상태를 항상 일치시킵니다.**
  - 코드를 수정했다면 apply 후 PR을 올리고, 리뷰로 코드가 바뀌면 다시 apply합니다.
  - Terraform으로 관리하는 리소스는 콘솔에서 직접 수정하지 않습니다. (`ManagedBy = terraform` 태그로 구분)
- 다음 파일은 커밋하지 않습니다: `.terraform/`, `*.tfstate`, `*.tfvars`
- `.terraform.lock.hcl`은 provider 버전 고정용이므로 커밋합니다.

## 네이밍 / 태그

- 리소스 이름: `kiri-{env}-*` (예: `kiri-dev-api`)
- 공통 태그는 provider의 `default_tags`로 자동 부여됩니다.

| 태그 | 값 |
|---|---|
| `Project` | `kiri` |
| `Environment` | `bootstrap` / `global` / `dev` / `prod` |
| `ManagedBy` | `terraform` |

## 비용 알람 (global)

- **AWS Budgets**: 월 예산(`monthly_budget_usd`, 기본 20 USD) 기준
  - 실제 사용액 50%, 100% 초과 시
  - 예상 사용액 100% 초과 시
  - 크레딧을 제외한 실제 사용량 기준(`include_credit = false`)
- **Cost Anomaly Detection**: AWS 기본 서비스 모니터(`Default-Services-Monitor`)를 import하여 사용
  - 이상 비용 5 USD 이상 시 일 단위 이메일 알림

> 이메일 수신자는 AWS가 보내는 확인 메일을 승인해야 알림을 받을 수 있습니다.
> `alert_emails`에 주소를 추가한 뒤에는 수신자가 확인 메일을 승인했는지, 콘솔에서 Active 상태인지 확인합니다.

## dev 환경 구성

| 리소스 | 사양 |
|---|---|
| VPC | `10.0.0.0/16`, AZ 2개 (`2a`, `2c`) |
| Subnet | Public `10.0.0.0/24`, `10.0.1.0/24` / Private `10.0.10.0/24`, `10.0.11.0/24` |
| NAT Gateway | 없음 (비용 절감, private subnet은 VPC 내부 통신만 가능) |
| EC2 | `t4g.small` (arm64), Amazon Linux 2023, gp3 20GB, Elastic IP, CPU 크레딧 `standard` |
| RDS | PostgreSQL 18, `db.t4g.micro`, Single-AZ, gp3 20GB, private subnet |
| CloudWatch Logs | `/kiri/dev/app`, 보관 7일 |

- **보안 그룹**
  - EC2: 80, 443만 허용 (22번 포트는 닫혀 있음)
  - RDS: 5432는 EC2 보안 그룹에서 오는 요청만 허용
- **24시간 운영**: 야간·주말에도 작업이 있어 자동 중지 스케줄은 적용하지 않았습니다.

> EC2가 arm64이므로 Docker 이미지는 `linux/arm64`로 빌드해야 합니다.

## 서버 접속 (SSM Session Manager)

SSH 대신 SSM Session Manager로 접속합니다. 키 파일이 필요 없고, IAM 권한으로 접속을 제어합니다.

1. 로컬에 [Session Manager 플러그인](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) 설치 (최초 1회)
2. 인스턴스 ID 확인

   ```bash
   cd infra/envs/dev
   terraform output ec2_instance_id
   ```

3. 접속

   ```bash
   aws ssm start-session --target <ec2_instance_id> --profile kiri
   ```

### 로컬에서 RDS 접속 (포트 포워딩)

RDS는 private subnet에 있어 로컬에서 직접 접속할 수 없습니다.
EC2를 경유하는 포트 포워딩으로 로컬 `15432` 포트를 RDS `5432`에 연결합니다.

```bash
aws ssm start-session --target <ec2_instance_id> --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters host=<rds_address>,portNumber=5432,localPortNumber=15432 --profile kiri
```

세션이 열려 있는 동안 DB 클라이언트(DataGrip, DBeaver 등)에서 `localhost:15432`로 접속합니다.
`rds_address`는 `terraform output rds_address`로 확인합니다.

## 접속 정보 (Parameter Store)

DB 접속 정보는 Terraform이 생성해서 Parameter Store에 SecureString으로 저장합니다.
비밀번호는 `random_password`로 생성되며, 코드나 git에는 남지 않습니다.

| 이름 | 값 |
|---|---|
| `/kiri/dev/DB_URL` | `jdbc:postgresql://<rds_endpoint>/kiri` |
| `/kiri/dev/DB_USER` | DB 사용자 이름 |
| `/kiri/dev/DB_PW` | DB 비밀번호 |

이름은 `application.yaml`의 환경변수(`DB_URL`, `DB_USER`, `DB_PW`)와 같습니다.

```bash
aws ssm get-parameter --name /kiri/dev/DB_PW --with-decryption --query Parameter.Value --output text --profile kiri
```

- EC2의 IAM Role은 `/kiri/dev/*` 경로만 읽을 수 있습니다.
- `JWT_SECRET` 등 Terraform이 만들지 않는 값도 같은 경로에 추가합니다.

  ```bash
  aws ssm put-parameter --name /kiri/dev/JWT_SECRET --type SecureString --value "<값>" --profile kiri
  ```

## EC2 초기 설정 (user_data)

EC2가 처음 부팅될 때 `modules/ec2/user_data.sh`가 실행되어 다음을 설치합니다.

- swap 2GB
- Docker, Docker Compose 플러그인
- Nginx
- certbot (Let's Encrypt)
- psql (RDS 접속 확인용)

실행 결과는 서버에서 확인합니다.

```bash
sudo cloud-init status --wait
sudo tail -n 40 /var/log/cloud-init-output.log
```

> **user_data는 최초 부팅 시 1회만 실행됩니다.**
> 스크립트를 수정해도 기존 인스턴스에는 반영되지 않고, apply 시 인스턴스가 재부팅만 됩니다.
> 기존 서버에 반영하려면 SSM으로 접속해서 직접 실행합니다.
>
> `user_data.sh`는 줄바꿈이 **LF**여야 합니다. (CRLF면 스크립트가 실행되지 않음)

## 주의 사항

- **AMI 자동 업데이트 무시**: 최신 AL2023 AMI를 조회하지만 `ignore_changes = [ami]`로 설정되어, 새 AMI가 나와도 인스턴스가 재생성되지 않습니다.
- **RDS 생성/삭제**: 10분 정도 걸립니다.
- **dev RDS는 삭제 보호가 꺼져 있고, 삭제 시 최종 스냅샷을 남기지 않습니다.** prod에서는 `deletion_protection = true`, `skip_final_snapshot = false`로 설정합니다.
