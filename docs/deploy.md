# 배포

## 한눈에 보기

| 언제 | 무엇이 실행되나 |
|---|---|
| develop/main으로 PR을 열거나 커밋을 추가할 때 | 테스트 (`CI`) |
| develop에 머지될 때 | 테스트 → dev 서버 자동 배포 (`Deploy dev`) |
| prod 배포 | main에서 수동 실행 (`Deploy prod`) |

> `infra/terraform/**`, `*.md`만 바뀐 경우에는 테스트와 배포를 건너뜁니다.

dev 배포 흐름:

```
develop 머지
  → 테스트
  → 이미지 빌드 후 ECR push (태그: dev-<commit sha>)
  → EC2에서 컨테이너 교체 → 헬스체크
    └ 실패하면 이전 버전으로 자동 롤백 (Actions에는 실패로 표시)
  → nginx 반영 (인증서·갱신 timer가 없으면 설치, 설정은 바뀐 경우만 반영)
```

- 배포 중 30~60초 정도 dev 서버 요청이 실패할 수 있습니다. (무중단 배포 아님)
- dev 서버 주소: `https://kiri-dev.halo-app.co.kr`
- Swagger: `https://kiri-dev.halo-app.co.kr/swagger-ui/index.html` (basic auth 계정은 [infra/README.md](../infra/README.md#값-조회-parameter-store)에서 조회)

prod 배포:

1. develop → main PR 머지
2. GitHub Actions › `Deploy prod` › **Run workflow** (branch: `main`)
3. 테스트 → 이미지 빌드 후 ECR push (태그: `prod-<commit sha>`) → 이후는 dev와 동일

- prod 서버 주소: `https://kiri.halo-app.co.kr` (외부에는 `/api/**`, `/actuator/health`만 공개)
- 아래 확인·실패·롤백 방법은 prod도 같습니다. (`Deploy dev` → `Deploy prod`, `/kiri/dev` → `/kiri/prod`)

## 배포 결과 확인

| 확인할 것 | 위치 |
|---|---|
| 성공/실패, 실패 원인 | GitHub Actions › `Deploy dev` › `deploy` › `Deploy to EC2` › "배포 스크립트 출력" |
| 앱 로그 | AWS 콘솔 › CloudWatch › Log groups › `/kiri/dev/app` |
| nginx 로그 | 같은 로그 그룹의 `nginx` 스트림 |
| 서버 상태 | `curl https://kiri-dev.halo-app.co.kr/actuator/health` |

### 배포가 실패했을 때

| Actions 메시지 | 확인할 것 |
|---|---|
| `컨테이너가 실행 중이 아님` | 앱이 뜨자마자 종료됨. 출력의 "컨테이너 최근 로그"에서 원인 확인 (DB 접속, 환경변수 누락, Flyway 오류 등) |
| `헬스체크 시간 초과` | 120초 안에 기동하지 못함. CloudWatch 로그 확인 |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | GitHub Environment 설정 또는 `AWS_ROLE_ARN` 변수 확인 |
| `배포 대상이 1대가 아닙니다` | dev EC2가 중지되어 있는지 확인 |
| `nginx 설정 검사 실패` | `infra/nginx/dev.conf` 문법 오류. 출력의 `nginx: [emerg]` 줄에서 원인 확인. 기존 nginx 설정은 그대로 유지됨 |
| `nginx 반영 실패` | 출력의 nginx 로그에서 원인 확인. 이전 nginx 설정으로 자동 복구됨 |
| `인증서 발급 실패` | 도메인 DNS가 EC2를 가리키는지, 80 포트가 열려 있는지 확인. 같은 도메인으로 1시간에 5번까지만 재시도 가능 |
| `ParameterNotFound` (nginx) | `/kiri/dev/nginx/` basic auth 계정이 없음. `infra/terraform/envs/dev`에서 apply |

앱 단계에서 실패하면 서버는 자동으로 이전 버전으로 돌아가 있으니, 원인을 고쳐서 다시 머지하면 됩니다.
nginx 단계에서 실패하면 앱은 새 버전으로 떠 있고 nginx는 배포 전 상태 그대로입니다.

## 롤백 (배포는 성공했는데 기능에 문제가 있을 때)

1. GitHub Actions › `Deploy dev`에서 되돌리고 싶은 시점의 **성공한 실행**을 선택
2. **Re-run all jobs**

그 시점의 커밋으로 다시 빌드해서 배포합니다.

## 환경변수 추가

앱에 새 환경변수가 필요하면 `infra/terraform/modules/app_parameters/main.tf`에 이름을 추가하고 환경별로 `terraform apply`한 뒤, 아래 명령으로 값을 입력합니다. 다음 배포부터 컨테이너에 주입됩니다.

PowerShell:

```powershell
$value = Read-Host "값 입력"
aws ssm put-parameter --name /kiri/dev/<변수명> --type SecureString --value $value --overwrite --profile kiri --region ap-northeast-2
```

- 값을 명령에 직접 쓰지 않고 `Read-Host`로 입력받습니다. 직접 쓰면 명령 히스토리에 시크릿이 남습니다.
- 이름의 마지막 부분이 그대로 환경변수 이름이 됩니다. (`/kiri/dev/API_KEY` → `API_KEY`)
- `/kiri/dev/` **바로 아래**에 만들어야 합니다. 하위 경로(예: `/kiri/dev/nginx/...`)는 앱에 주입되지 않습니다.
- 값은 **한 줄**이어야 합니다.
- 이미 있는 값을 바꾼 뒤에는 재배포합니다.
- Git Bash에서는 `/kiri/...`가 Windows 경로로 바뀌어 실패하므로 명령 앞에 `MSYS_NO_PATHCONV=1`을 붙입니다.

## 참고: 구성

| 파일 | 역할 |
|---|---|
| `.github/workflows/ci.yml` | PR 테스트 |
| `.github/workflows/deploy-dev.yml` | develop 머지 시 dev 배포 |
| `.github/workflows/deploy-prod.yml` | prod 수동 배포 |
| `.github/workflows/_test.yml`, `_deploy.yml` | 공통 테스트, 공통 배포 단계 |
| `scripts/deploy/ssm-deploy.sh` | Actions에서 EC2로 배포 명령 전송 |
| `scripts/deploy/deploy.sh` | EC2에서 컨테이너 교체, 헬스체크, 롤백, nginx 설정·인증서 반영 |
| `infra/nginx/` | nginx, certbot 컨테이너 설정 ([infra/README.md](../infra/README.md#nginx-https-basic-auth)) |

- AWS 인증은 GitHub OIDC를 사용합니다. (Access Key 없음)
- dev 배포는 GitHub Environment `dev`에서 develop 브랜치만 허용합니다.
- prod 배포는 GitHub Environment `prod`에서 main 브랜치만 허용하며, 자동 배포 없이 수동 실행합니다.
- 인프라와 최초 설정은 [infra/README.md](../infra/README.md)를 참고합니다.
