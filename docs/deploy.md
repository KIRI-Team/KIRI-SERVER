# 배포

## 한눈에 보기

| 언제 | 무엇이 실행되나 |
|---|---|
| develop/main으로 PR을 열거나 커밋을 추가할 때 | 테스트 (`CI`) |
| develop에 머지될 때 | 테스트 → dev 서버 자동 배포 (`Deploy dev`) |
| prod 배포 | 현재 미구성 (prod 인프라 구성 후 main에서 수동 실행) |

> `infra/**`, `*.md`만 바뀐 경우에는 테스트와 배포를 건너뜁니다.

dev 배포 흐름:

```
develop 머지
  → 테스트
  → 이미지 빌드 후 ECR push (태그: dev-<commit sha>)
  → EC2에서 컨테이너 교체 → 헬스체크
    └ 실패하면 이전 버전으로 자동 롤백 (Actions에는 실패로 표시)
```

- 배포 중 30~60초 정도 dev 서버 요청이 실패할 수 있습니다. (무중단 배포 아님)
- dev 서버 주소: `http://<EIP>:8080` (도메인 확보 전 임시)

## 배포 결과 확인

| 확인할 것 | 위치 |
|---|---|
| 성공/실패, 실패 원인 | GitHub Actions › `Deploy dev` › `deploy` › `Deploy to EC2` › "배포 스크립트 출력" |
| 앱 로그 | AWS 콘솔 › CloudWatch › Log groups › `/kiri/dev/app` |
| 서버 상태 | `curl http://<EIP>:8080/actuator/health` |

### 배포가 실패했을 때

| Actions 메시지 | 확인할 것 |
|---|---|
| `컨테이너가 실행 중이 아님` | 앱이 뜨자마자 종료됨. 출력의 "컨테이너 최근 로그"에서 원인 확인 (DB 접속, 환경변수 누락, Flyway 오류 등) |
| `헬스체크 시간 초과` | 120초 안에 기동하지 못함. CloudWatch 로그 확인 |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | GitHub Environment 설정 또는 `AWS_ROLE_ARN` 변수 확인 |
| `배포 대상이 1대가 아닙니다` | dev EC2가 중지되어 있는지 확인 |

실패하면 서버는 자동으로 이전 버전으로 돌아가 있으니, 원인을 고쳐서 다시 머지하면 됩니다.

## 롤백 (배포는 성공했는데 기능에 문제가 있을 때)

1. GitHub Actions › `Deploy dev`에서 되돌리고 싶은 시점의 **성공한 실행**을 선택
2. **Re-run all jobs**

그 시점의 커밋으로 다시 빌드해서 배포합니다.

## 환경변수 추가

앱에 새 환경변수가 필요하면 Parameter Store `/kiri/dev/` 아래에 추가합니다. 다음 배포부터 컨테이너에 주입됩니다.

PowerShell:

```powershell
$value = Read-Host "값 입력"
aws ssm put-parameter --name /kiri/dev/<변수명> --type SecureString --value $value --profile kiri --region ap-northeast-2
```

- 값을 명령에 직접 쓰지 않고 `Read-Host`로 입력받습니다. 직접 쓰면 명령 히스토리에 시크릿이 남습니다.
- 이름의 마지막 부분이 그대로 환경변수 이름이 됩니다. (`/kiri/dev/API_KEY` → `API_KEY`)
- 값은 **한 줄**이어야 합니다.
- 이미 있는 값을 바꿀 때는 `--overwrite`를 붙이고 재배포합니다.
- Git Bash에서는 `/kiri/...`가 Windows 경로로 바뀌어 실패하므로 명령 앞에 `MSYS_NO_PATHCONV=1`을 붙입니다.

## 참고: 구성

| 파일 | 역할 |
|---|---|
| `.github/workflows/ci.yml` | PR 테스트 |
| `.github/workflows/deploy-dev.yml` | develop 머지 시 dev 배포 |
| `.github/workflows/deploy-prod.yml` | prod 수동 배포 (미구성) |
| `.github/workflows/_test.yml`, `_deploy.yml` | 공통 테스트, 공통 배포 단계 |
| `scripts/deploy/ssm-deploy.sh` | Actions에서 EC2로 배포 명령 전송 |
| `scripts/deploy/deploy.sh` | EC2에서 컨테이너 교체, 헬스체크, 롤백 |

- AWS 인증은 GitHub OIDC를 사용합니다. (Access Key 없음)
- dev 배포는 GitHub Environment `dev`에서 develop 브랜치만 허용합니다.
- prod는 private 레포 요금제상 승인자(Required reviewers) 기능을 쓸 수 없어, main에서 수동 실행하는 방식으로 운영합니다.
- 인프라와 최초 설정은 [infra/README.md](../infra/README.md)를 참고합니다.
