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
```

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
