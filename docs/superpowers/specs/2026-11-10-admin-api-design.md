# KIRI 관리자 API 설계 (1차)

- 작성일: 2026-11-10
- 범위: KIRI-SERVER 레포의 관리자 API 1차. 관리자 화면(프론트)은 별도 레포에서 만들고 이 문서의 범위 밖이다.
- 원본 기획: "KIRI 관리자페이지 기획 개요" (운영 대시보드 ~ 공통 정책, 12개 절)

표기: **[결정]** = 사용자가 정한 것, **[설계자 판단]** = 문서 작성자가 정한 것(뒤집어도 됨), **[빌더 재량]** = 구현자가 정하면 되는 것.

---

## 1. 목적과 배경

KIRI 운영자가 미션 검수, 수동 등록, 끼리 픽 편성, 회원·가족·일정 조회, 외부 수집 관리를 한곳에서 할 수 있도록 서버에 관리자 전용 API를 만든다.
운영 성과는 접속량이 아니라 **가족이 활동을 실행·완료했는지, 다음 활동으로 이어졌는지**로 본다. 대시보드 KPI도 이 기준으로 설계한다.

지금 서버에는 관리자 개념이 전혀 없다. `SecurityConfig`는 앱 사용자 JWT 하나만 알고 `anyRequest().authenticated()`로 끝난다.
반면 검수와 수집 관리의 데이터 기반은 이미 있다.
- `ExternalRecord.processStatus = REVIEW_REQUIRED` + `reviewReason`이 검수 대기열이 된다.
- `ExternalSyncState`, `ExternalSyncRetry`가 수집 현황과 실패 재처리의 근거다.
- `Mission.titleEdited` / `checklistEdited`가 "운영자 수정값 보호"의 시초다.

## 2. 목표 / 비목표 / 반목표

### 목표 (1차)
1. 관리자 인증과 감사 로그
2. 미션 검수 (대기열, 원본 비교, 보완, 재검증, 승인·보류·제외)
3. 미션·장소 수동 등록·수정, 질문 등록·수정·중지
4. 끼리 픽 편성 + 앱 노출 API
5. 회원·가족·일정·리뷰 기본 조회
6. 외부 수집 관리 (현황, 중단·재개, 수동 실행, 실패 재처리)
7. 운영 대시보드 KPI 중 DB 집계로 계산 가능한 것
8. 핵심 계측: 앱 행동 이벤트 수집 API와 저장소

### 비목표 (2차 이후)
- 상세 시각화, 리텐션 분석, 퍼널 분석 화면용 API
- 관계 경험 설문, 체험단, 제휴
- 고객지원(건의·신고), 배너·알림 관리, 추천 운영 화면
- 데이터 내보내기, KPI 목표 관리 화면
- 쿠폰·티켓·포인트·정산
- 권한 세분화

### 반목표 (이렇게 되면 실패)
- 관리자 토큰으로 앱 API가 열리거나, 앱 토큰으로 관리자 API가 열린다.
- 운영자가 고친 값이 다음 외부 재수집에서 덮어써진다.
- 검수 승인만으로 종료·취소된 미션이 다시 공개된다.
- 재처리·재실행 때 중복 원본이나 중복 미션이 생긴다.
- 노출·클릭을 실제 방문·매출처럼 표시하거나, 한 일정의 완료가 여러 끼리 픽에 중복 집계된다.
- 지금 동작하는 자동 수집·자동 공개 흐름이 깨진다.

## 3. 제약

- Spring Boot 4.1 / Java 25 / PostgreSQL / Flyway(현재 V27까지) / JPA `ddl-auto: validate`. 스키마 변경은 전부 Flyway 마이그레이션으로 한다.
- 기존 패키지 컨벤션(`controller` / `controller/docs` / `converter` / `dto` / `entity` / `enums` / `exception/code` / `repository` / `service`)과 `ApiResponse` 응답 포맷, `BaseErrorCode` 계열 에러 코드를 따른다.
- 집계는 한국 시간(Asia/Seoul) 기준이다. 테스트 계정과 테스트 가족은 기본 제외한다.
- 프론트는 별도 레포다. 서버는 OpenAPI 스펙으로 계약을 제공하고, 프론트는 이 스펙으로 타입을 생성한다.

## 4. 사용자가 정한 것

- **[결정]** 관리자 API는 이 서버 레포 안에 만든다. 관리자 화면은 별도 레포로 만든다.
- **[결정]** 권한은 `ADMIN` 하나다. 조회·편집·승인·원문 열람·내보내기를 역할로 나누지 않는다.
- **[결정]** 로그인은 **아이디 + 비밀번호**다. 이메일 로그인도, 소셜 로그인도 쓰지 않는다.

## 5. 설계

### 5.1 패키지 구조 [설계자 판단]

관리자 코드는 `com.kiri.server.admin` 아래 기능별로 둔다.

```
admin/
  auth/      관리자 계정, 로그인, 토큰
  audit/     감사 로그
  review/    미션 검수
  content/   미션·장소·질문 수동 관리
  pick/      끼리 픽
  lookup/    회원·가족·일정·리뷰 조회
  sync/      수집 관리
  dashboard/ KPI
```

관리자 서비스는 기존 도메인의 엔티티와 리포지토리를 그대로 쓴다. 상태 변경은 엔티티의 도메인 메서드로 하고, 필요한 메서드는 엔티티에 추가한다. 관리자 서비스에서 필드를 직접 바꾸지 않는다.
앱이 쓰는 끼리 픽 노출 API와 이벤트 수집 API는 앱 기능이므로 `domain/` 아래에 둔다(`domain/pick`, `domain/event`).

### 5.2 인증과 보안

**관리자 계정** — 새 테이블 `admin`

| 컬럼 | 설명 |
|---|---|
| `admin_id` | PK |
| `login_id` | 로그인 아이디, unique |
| `password_hash` | BCrypt |
| `name` | 표시 이름 (감사 로그·담당자 표시에 사용) |
| `enabled` | 비활성화하면 로그인 불가, 기존 토큰도 거부 |
| `refresh_token_hash` | 리프레시 토큰 해시 (회원과 같은 방식) |
| `last_login_at`, `created_at`, `updated_at` | |

- 역할 컬럼은 두지 않는다. 인증된 관리자는 전부 `ROLE_ADMIN`이다. **[결정]**
- **[결정]** 최초 관리자를 자동으로 만드는 기능과 로그인 실패 잠금은 두지 않는다.
- **[설계자 판단]** 첫 관리자 계정은 개발자가 BCrypt 해시를 만들어 DB에 직접 INSERT한다. 비밀번호는 마이그레이션 SQL에 넣지 않는다. 그 뒤의 계정은 관리자 API로 만든다.
- 로그인 실패 응답은 아이디가 없을 때와 비밀번호가 틀렸을 때를 구분하지 않는다.

**토큰**
- `JwtProvider`에 관리자 토큰을 추가한다. `type` 클레임은 `admin_access` / `admin_refresh`로 하고, subject는 `adminId`다.
- 앱 필터는 `type == access`만 받으므로 관리자 토큰으로는 앱 API가 열리지 않는다. 관리자 필터도 `admin_access`만 받는다. 이 분리가 반목표 1번을 막는다.
- **[설계자 판단]** 액세스 토큰은 1시간, 리프레시 토큰은 7일이다. 서명 키는 같은 시크릿을 쓰고 type 클레임으로 구분한다. 키를 분리하고 싶다면 빌더 재량이다.
- 관리자 필터는 매 요청마다 `admin.enabled`를 확인한다. 관리자 수가 적어서 비용이 작고, 비활성화를 즉시 반영할 수 있다.

**SecurityFilterChain**
- `@Order(1)` 체인에 `securityMatcher("/api/admin/**")`, `AdminJwtAuthFilter`, `hasRole("ADMIN")`을 둔다. 공개 경로는 `/api/admin/auth/login`, `/api/admin/auth/reissue`뿐이다.
- 기존 앱 체인은 `/api/admin/**` 이외의 경로만 처리한다. 기존 동작은 바꾸지 않는다.
- 관리자 체인에만 CORS를 연다. 허용 Origin은 `admin.cors.allowed-origins` 설정값으로 둔다(local/dev/prod 별도).
- 관리자 API는 `@AuthenticationPrincipal Long adminId`로 현재 관리자를 받는다(앱의 memberId 패턴과 동일).

**API**
- `POST /api/admin/auth/login`, `POST /api/admin/auth/reissue`, `POST /api/admin/auth/logout`
- `GET /api/admin/admins`, `POST /api/admin/admins`, `PATCH /api/admin/admins/{id}` (이름 변경·비활성화·비밀번호 재설정)
- `PATCH /api/admin/me/password`

### 5.3 감사 로그

새 테이블 `admin_audit_log`

| 컬럼 | 설명 |
|---|---|
| `admin_audit_log_id` | PK |
| `admin_id` | 수행한 관리자 |
| `action` | `CREATE` / `UPDATE` / `APPROVE` / `HOLD` / `REJECT` / `HIDE` / `RESTORE` / `PUBLISH` / `VIEW_SENSITIVE` / `SYNC_RUN` / `SYNC_PAUSE` / `SYNC_RESUME` / `RETRY` / `LOGIN` 등 |
| `target_type`, `target_id` | `MISSION`, `PLACE`, `EXTERNAL_RECORD`, `KIRI_PICK`, `QUESTION`, `MEMBER`, `FAMILY`, `SYNC`, `ADMIN` … |
| `reason` | 사유. 아래 필수 목록에서는 비어 있으면 400 |
| `before_data`, `after_data` | JSONB. 변경 전후 값 |
| `created_at` | |

- 기록은 각 관리자 서비스 메서드 안에서 `AdminAuditLogger.log(...)`로 남기고, 업무 변경과 같은 트랜잭션에 넣는다. 변경이 롤백되면 로그도 롤백된다. **[설계자 판단]**
- **사유 필수:** 검수 보류·제외, 미션 숨김·복구, 공개 중 미션 수정, 민감정보 열람, 수집 중단.
- 기획서의 "수정 전후 버전" 요구는 이 테이블의 before/after로 충족한다. 별도 버전 테이블은 만들지 않는다.
- 조회 API: `GET /api/admin/audit-logs?targetType=&targetId=&adminId=&from=&to=`. 미션 상세 화면에서 해당 미션의 이력을 보여줄 때도 이 API를 쓴다.

### 5.4 테스트 계정 제외

- `member.is_test`, `family.is_test`(boolean, 기본 false)를 추가한다.
- 관리자 API `PATCH /api/admin/members/{id}/test-flag`, `PATCH /api/admin/families/{id}/test-flag`로 지정한다.
- KPI와 집계 쿼리는 `is_test = false`만 센다. 조회 화면에는 테스트 계정도 나오되 표시한다.
- **[설계자 판단]** 가족에 테스트 회원이 한 명이라도 있으면 그 가족이 자동으로 테스트가 되지는 않는다. 가족 플래그는 따로 지정한다.

### 5.5 미션 검수

**상태를 세 갈래로 나눠 관리한다** (기획서 6절)
1. 원본 처리 상태: 기존 `ExternalRecord.processStatus` (`PROCESSED` / `REVIEW_REQUIRED` / `IGNORED`)
2. 검수 업무 상태: 새 테이블 `mission_review_task`
3. 미션 공개 상태: 기존 `Mission.status`

**검수 화면은 두 탭이다** **[결정]**

지금 미션 생성에는 관문이 두 개 있다. 첫째는 검증(`MissionDraftFactory`)이고, 자동 공개 설정과 무관하게 항상 동작한다. 둘째는 공개 스위치(`mission.auto-publish`)로, 검증을 통과한 건에만 적용된다. 스위치가 꺼져 있으면 검증을 통과한 미션은 `CHECK_REQUIRED`로 남는다. 이 미션을 하나씩 공개할 경로가 지금은 없고, 스위치를 켜야만 야간 배치(`publishPending`)가 한꺼번에 공개한다. 두 탭은 이 두 관문에 하나씩 대응한다. 운영 서버의 스위치 설정과 상관없이 동작한다.

| 탭 | 대상 | 운영자가 하는 일 |
|---|---|---|
| **① 검증 실패 (수정)** | `external_record.process_status = 'REVIEW_REQUIRED'` | 원본 비교, 값 보완, 재검증, 승인·보류·제외 (아래 API) |
| **② 공개 대기 (노출 스위치)** | `process_status = 'PROCESSED'`이고 연결된 `mission.status = 'CHECK_REQUIRED'` | 미리보기를 보고 노출 여부를 정한다. **켜면** `ACTIVE`로 공개하고(기간이 지났으면 즉시 `ENDED`), **끄면** `HIDDEN`으로 숨긴다. 어느 쪽이든 탭에서 빠진다 |

- 탭 ①에서 승인한 미션도 공개 규칙(아래)에 따라 바로 공개된다. 탭 ②를 다시 거치지 않는다. 운영자가 이미 직접 확인했기 때문이다. **[설계자 판단]**
- 탭 ②의 "끄기"로 숨긴 미션은 재수집에도 다시 노출되지 않는다. 동기화의 `reactivate`는 `CHECK_REQUIRED` / `ENDED`만 되살리고 `HIDDEN`은 건드리지 않는다. 이 동작은 테스트로 고정한다.
- 탭 ② API: `GET /api/admin/reviews/pending-publish`(필터는 탭 ①과 같음), `POST /api/admin/reviews/pending-publish/{missionId}/publish`, `POST /api/admin/reviews/pending-publish/{missionId}/hide`(사유 필수). 여러 건을 한 번에 처리하는 `POST .../bulk`(missionId 목록 + publish/hide)도 둔다.
- **[설계자 판단]** "스위치"는 미션별 노출 on/off로 해석했다. 전역 `mission.auto-publish` 설정을 관리자 화면에서 바꾸는 기능은 넣지 않았다. 이 설정은 지금처럼 환경변수로 둔다.

**대기열의 기준 (탭 ①)**
- 대기열은 `external_record.process_status = 'REVIEW_REQUIRED'`인 레코드다. 업무 상태는 `mission_review_task`에서 LEFT JOIN으로 붙인다.
- `external_record.review_requested_at`(TIMESTAMPTZ)을 추가하고 `markReviewRequired()`에서 기록한다. 대기시간은 이 값으로 계산한다. 마이그레이션에서 기존 `REVIEW_REQUIRED` 행은 `updated_at`(없으면 `created_at`)으로 채운다.

**`mission_review_task`**

| 컬럼 | 설명 |
|---|---|
| `mission_review_task_id` | PK |
| `external_record_id` | FK. 열린 작업은 레코드당 1개(부분 unique 인덱스) |
| `status` | `OPEN` / `ON_HOLD` / `APPROVED` / `REJECTED` |
| `assignee_admin_id` | 담당자, nullable |
| `review_reason` | 작업이 생길 때의 사유 스냅숏 |
| `note` | 운영 메모 |
| `resolved_by`, `resolved_at` | |

- 작업 행은 관리자가 처음 손댈 때(담당자 지정, 보류 등) 생긴다. 대기열 조회에서는 작업 행이 없으면 `OPEN`으로 보여준다. **[설계자 판단]**
- 같은 레코드가 나중에 다시 `REVIEW_REQUIRED`가 되면 새 작업이 열린다. 이전 작업은 이력으로 남는다.

**API**
- `GET /api/admin/reviews` — 필터: 검수 사유, 지역(`place.region_code`), 카테고리, 출처(`external_source`), 대기시간 구간, 담당자, 업무 상태. 페이지네이션.
- `GET /api/admin/reviews/{externalRecordId}` — 비교 화면용 묶음:
  - 외부 원본(`raw_data`)과 직전 원본(`external_record_history` 최신 1건), 그리고 둘 사이에 바뀐 필드
  - 자동 생성 결과: 원본으로 `MissionDraftFactory`를 다시 돌린 결과와 검증 실패 사유
  - 기존 공개본: 연결된 `Mission`과 체크리스트, `Place`
  - 중복 후보: `MissionGenerationService`가 쓰는 중복 판정 로직이 찾는 후보 미션 목록
  - 이 레코드의 검수 작업 이력과 감사 로그
- `PATCH /api/admin/reviews/{id}/assignee`
- `POST /api/admin/reviews/{id}/revalidate` — 운영자가 보완한 값(제목, 카테고리, 좌표, 기간, 필수 활동 등)을 원본 위에 덮어 검증만 돌린다. 저장하지 않고 통과 여부와 실패 사유만 돌려준다.
- `POST /api/admin/reviews/{id}/approve` — 보완 값과 함께 승인한다.
  - 검증을 다시 돌려서 실패하면 400을 돌려준다.
  - 미션이 없으면 `Place`(매칭되면 기존 장소)와 `Mission`을 만들고, 미션이 있으면 수정한다. 운영자가 넣은 필드는 잠금 필드(5.6)로 표시한다.
  - `record.markProcessed(mission)` 후 작업을 `APPROVED`로 바꾼다.
  - **공개 규칙:** 미션이 `CHECK_REQUIRED`면 `restoreFromCheck()`로 `ACTIVE`로 바꾸고, 같은 트랜잭션에서 `endIfPeriodOver(today)`를 호출해 기간이 지난 미션은 바로 `ENDED`로 보낸다(`restoreFromCheck()` 자체는 기간을 보지 않는다). 새 미션이면 `ACTIVE`로 만든다. 기간형 미션의 시작일이 아직 안 왔어도 `ACTIVE`로 둔다. 앱 조회 쿼리가 이미 기간으로 거르므로 별도의 대기 상태는 만들지 않는다. **[설계자 판단]** 미션이 `ENDED` / `CANCELLED` / `HIDDEN`이면 **상태를 바꾸지 않는다**(반목표 3번). 다시 공개하려면 콘텐츠 관리에서 사유를 적고 명시적으로 복구한다.
- `POST /api/admin/reviews/{id}/hold` (사유 필수) — 작업만 `ON_HOLD`로 바꾼다. 원본 상태는 그대로다.
- `POST /api/admin/reviews/{id}/reject` (사유 필수) — `ReviewReason.ADMIN_EXCLUDED`를 새로 추가하고 `record.markIgnored(ADMIN_EXCLUDED)`를 호출한다. 연결된 미션이 `ACTIVE`/`CHECK_REQUIRED`면 `HIDDEN`으로 바꾼다.
  - 이미 `ExternalRecordSyncService.decide()`가 `IGNORED` 레코드를 건너뛰므로, 재수집해도 다시 살아나지 않는다. 다만 이 동작이 원본 내용이 바뀐 경우에도 유지되는지 테스트로 고정한다.

**승인 후 원본이 다시 바뀐 경우** **[결정]**
- 운영자가 보완한 값은 원본 위에 계속 덮어 쓰지 않는다. 원본이 바뀌면 동기화는 원본만 보고 다시 검증한다. 원본이 여전히 검증에 실패하면 레코드는 다시 `REVIEW_REQUIRED`가 되고 미션은 `CHECK_REQUIRED`로 내려간다. 이 동작은 지금과 같다.
- 다시 들어온 건은 대기열에서 **"이전에 승인됨"** 으로 표시한다. 같은 `external_record_id`에 `APPROVED` 상태의 이전 `mission_review_task`가 있으면 이 표시를 붙인다.
- 상세 화면에는 직전 승인 때 운영자가 넣은 보완 값을 함께 보여준다. 이를 위해 승인할 때 보완 값을 `mission_review_task.applied_overrides`(JSONB)에 저장한다. 프론트에는 "이전 보완 값 불러오기"로 다시 채워 넣을 수 있게 내려준다.

**재수집과 충돌**
검수 중인 레코드를 동기화가 동시에 건드릴 수 있다. 승인·제외는 `ExternalRecord`를 비관적 락으로 읽거나, `@Version` 낙관적 락으로 처리한다. 둘 중 무엇을 쓸지는 빌더 재량이지만, 충돌하면 409를 돌려주고 운영자에게 새로고침을 요구해야 한다.

### 5.6 운영자 수정값 보호 (잠금 필드)

- `mission.locked_fields`, `place.locked_fields`(`TEXT[]`, 기본 빈 배열)를 추가한다. **[설계자 판단]**
- 관리자가 고친 필드 이름을 여기에 넣는다. 자동 수집 경로(`Mission.updateContent`, `Place.fillMissingFrom` / `refreshImage`)는 잠긴 필드를 건너뛴다.
- 기존 `title_edited` → `TITLE`, `checklist_edited` → `CHECKLIST`로 마이그레이션한다. 기존 boolean 컬럼은 이번 단계에서는 남겨두고 읽지 않게 바꾼 뒤, 다음 마이그레이션에서 지운다. 2단계로 나누는 이유는 롤백 때문이다.
- 잠금 해제 API: `DELETE /api/admin/missions/{id}/locked-fields/{field}`. 해제하면 다음 수집 때 원본 값으로 돌아간다.
- 장소 공통 정보(이름, 주소, 좌표)는 `Place`에, 행사 정보(기간, 가격, 소개, 필수 활동)는 `Mission`에 둔다.
- 운영시간은 지금 `Mission`과 `Place` 양쪽에 있고, 동기화 때 `Mission.updateContent`가 미션 쪽 값을 덮어쓴다. **[설계자 판단]** 앱에 보이는 기준값은 미션의 운영시간이고, 장소의 운영시간은 새 미션을 만들 때 쓰는 기본값이다. 운영자가 미션 운영시간을 고치면 `mission.locked_fields`에 `OPENING_TIME` / `CLOSING_TIME`이 들어간다.
- `Place.fillMissingFrom`은 빈 필드만 채우고 이름·좌표를 덮어쓰지 않는다. 동기화가 실제로 덮어쓰는 장소 필드는 이미지(`refreshImage`)뿐이다. 그래서 `place.locked_fields`가 막아야 하는 대상은 1차에는 `IMAGE`뿐이다. 다만 운영자가 고친 필드는 전부 기록해 두어서, 나중에 동기화 로직이 바뀌어도 보호되게 한다.

### 5.7 수동 콘텐츠 등록·수정

**미션·장소**
- `MissionStatus`에 `DRAFT`를 추가한다. 앱 조회(`MissionQueryService`, `BucketService`)는 `ACTIVE`와 `ENDED`를 노출하고 `DRAFT`는 노출하지 않는다. 그래도 `DRAFT`가 새어 나가지 않는지 앱 조회 쿼리 전부를 테스트로 확인한다.
- 수동으로 만든 미션에는 `ExternalRecord`가 없다. 출처를 구분하려고 `mission.origin`(`EXTERNAL` / `MANUAL`)을 추가한다. 기존 행은 `EXTERNAL`로 채운다.
- API
  - `GET /api/admin/places`, `POST /api/admin/places`, `PATCH /api/admin/places/{id}`
  - `GET /api/admin/missions` (상태·카테고리·출처·지역·기간·키워드 필터)
  - `GET /api/admin/missions/{id}`
  - `POST /api/admin/missions` → `DRAFT`로 생성
  - `PATCH /api/admin/missions/{id}` — 공개 중인 미션을 고치면 사유 필수
  - `PUT /api/admin/missions/{id}/checklist`
  - `GET /api/admin/missions/{id}/preview` — 앱의 미션 상세 응답 DTO 형태로 돌려준다. 앱 상세 서비스는 `DRAFT`를 거부하므로 그대로 호출하지 않고, 상태 검사 없이 같은 converter로 응답을 만든다. 프론트는 이 응답을 앱 화면처럼 그린다.
  - `POST /api/admin/missions/{id}/publish` — `DRAFT` → `ACTIVE`. 필수값(제목, 장소·좌표, 카테고리, 타입, 기간형이면 기간, 소개, 필수 활동 1개 이상) 검증
  - `POST /api/admin/missions/{id}/hide`, `POST /api/admin/missions/{id}/restore` (사유 필수)
- 관리 항목: 미션명, 장소, 좌표, 카테고리, 상시·기간형, 운영기간, 소개, 필수 활동, 가격, 운영시간, 활동량(`walkingLevel`, 소요시간), 실내외, 이미지, 예약 링크.
  - 예약 링크는 지금 컬럼이 없다. `mission.reservation_url`을 추가한다.
- **이미지:** 기존 `ImageService`의 presigned URL 발급과 업로드 확정 흐름을 관리자용으로 연다(`POST /api/admin/images/presigned-url`, `POST /api/admin/images/confirm`). 지금 서비스는 memberId를 받으므로, 업로드 주체를 받는 부분을 관리자도 쓸 수 있게 일반화한다. 미션 이미지용 키 prefix를 따로 둔다.

**질문**
- `GET /api/admin/questions`, `POST /api/admin/questions`, `PATCH /api/admin/questions/{id}`, `POST /api/admin/questions/{id}/deactivate`
- 이미 배정된 질문의 문구를 고치면 과거 가족 화면의 문구가 바뀐다. 이를 막기 위해 **배정 이력(`FamilyQuestion`)이 하나라도 있는 질문은 문구 수정을 막고**, 새 질문을 만든 뒤 기존 질문은 중지하도록 안내한다. **[설계자 판단]** 문구 스냅숏 컬럼을 추가하는 것보다 단순하다.
- 카테고리별 기본 활동 템플릿은 지금 코드에서 `ChecklistPlanner`에 하드코딩되어 있다. 1차에서는 DB로 옮기지 않는다(비목표).

### 5.8 끼리 픽

**앱 쪽에는 지금 끼리 픽 노출 기능이 없다.** **[결정]** 끼리 픽은 앱의 미션 목록(`GET /api/v1/missions`, `MissionQueryService.getFeed`) **상단에 정렬**해서 노출한다. 별도의 노출 영역이나 앱 API는 만들지 않는다.

새 테이블 `kiri_pick`

| 컬럼 | 설명 |
|---|---|
| `kiri_pick_id` | PK |
| `mission_id` | 대표 미션. 장소에 미션이 여러 개면 운영자가 하나를 고른다 |
| `headline`, `description` | 추천 문구 |
| `image_url` | null이면 미션 이미지를 쓴다 |
| `display_order` | 미션 목록 상단에서의 순서 |
| `start_at`, `end_at` | 노출 기간 |
| `active` | 운영자 on/off |
| `pick_type` | `CURATION` / `COLLABORATION` / `PAID` |
| `partner_name` | nullable. 업체·캠페인 연결 전 임시 필드 |
| `created_by`, `created_at`, `updated_at` | |

- **노출 대상:** `active = true` AND `now`가 `start_at`과 `end_at` 사이 AND 미션 `status = ACTIVE` AND 미션 기간 조건(피드의 기존 기간 필터)을 만족. 미션이 종료·취소·숨김이면 편성이 살아 있어도 노출하지 않는다.
- **미션 목록 정렬 방식** **[설계자 판단]** — 지금 피드는 커서(마지막 미션 ID) 기반 페이지네이션이다. 정렬 키에 픽을 섞으면 커서가 깨지므로 다음처럼 한다.
  - **첫 페이지(커서 없음)** 맨 앞에 노출 대상 끼리 픽 미션을 `display_order` 순으로 붙인다.
  - 끼리 픽도 사용자가 고른 필터(카테고리, 지역, 거리)를 똑같이 적용한다. 필터에 맞지 않는 픽은 상단에 나오지 않는다.
  - 상단에 나온 픽 미션은 일반 목록 쿼리에서 제외해서 같은 미션이 두 번 나오지 않게 한다. 커서와 `hasNext`는 일반 목록 기준으로 그대로 계산한다.
  - `FeedItem`에 `kiriPick`(nullable: `kiriPickId`, `headline`, `description`, `imageUrl`) 필드를 추가한다. 앱은 이 값으로 픽 카드를 구분해 그린다. 필드 추가만 하므로 기존 앱 버전과 호환된다.
- **관리 API:** `GET/POST/PATCH /api/admin/kiri-picks`, `PATCH /api/admin/kiri-picks/order`(순서 일괄 변경). 관리자 목록에는 "편성은 활성이지만 미션이 비공개라 실제로는 노출 안 됨" 상태를 따로 표시한다.
- **성과 귀속:**
  - `bucket_item.source_kiri_pick_id`(nullable)를 추가한다. 앱이 끼리 픽에서 버킷에 저장할 때 이 값을 보낸다(버킷 저장 API 요청 필드 추가).
  - 일정과 완료는 버킷을 통해 그 끼리 픽 하나에만 귀속된다. 한 버킷은 하나의 픽에만 연결되므로 중복 집계가 생기지 않는다(반목표 5번). 같은 미션을 다른 픽에서 다시 저장하면 첫 저장의 출처를 유지한다. **[설계자 판단]**
  - 1차 성과 API: `GET /api/admin/kiri-picks/{id}/stats` → 저장 수, 일정 생성 수, 완료 수, 평균 별점, 재참여 의향 비율. 노출·클릭은 앱 이벤트(5.11)가 쌓이면 같은 응답에 추가한다. 이벤트가 없으면 `null`(미수집)로 보내고 0으로 보내지 않는다.

### 5.9 회원·가족·일정 기본 조회

- `GET /api/admin/members` — 회원 ID(`member_id`), 닉네임, 이메일로 검색. 회원은 소셜 로그인만 쓰므로 로그인 아이디가 없다. 탈퇴하면 닉네임과 이메일이 지워지므로, 탈퇴 회원은 회원 ID로만 찾을 수 있다. 가입·탈퇴 상태(`deletedAt`), 가족 연결 여부, 역할, 테스트 여부로 필터.
- `GET /api/admin/members/{id}` — 프로필, 소셜 제공자, 가입일, 가족과 가입 시각, 취향 설정 여부, 약관 동의, 활동 요약(버킷·일정·리뷰 수)
- `GET /api/admin/families`, `GET /api/admin/families/{id}` — 구성원과 역할, 가입 시각, 초대 상태, 지역(구 단위까지), 버킷·일정·리뷰 요약
- `GET /api/admin/families/{id}/schedules`, `GET /api/admin/schedules/{id}` — 일정, 참여자별 응답, 상태 이력(지금 있는 시각 컬럼 기준), 연결된 버킷과 리뷰
- **데이터 불일치 표시** [설계자 판단]: 일정 상세에서 "리뷰가 있는데 일정이 `COMPLETED`가 아님", "일정이 `COMPLETED`인데 버킷이 `COMPLETED`가 아님" 같은 불일치를 계산해 경고로 내려준다. 1차에서 **복구 API는 만들지 않는다.** 복구는 영향 범위를 봐야 하므로 2차에서 케이스별로 만든다.
- **민감정보:** 질문 답변 원문, 리뷰 사진, 가족 상세 주소·정밀 좌표는 기본 응답에 넣지 않는다. 별도 API `POST /api/admin/families/{id}/sensitive`(사유 필수)로 열람하고, 열람 사실을 `VIEW_SENSITIVE`로 감사 로그에 남긴다. 권한은 하나지만 사유와 이력은 남긴다.

### 5.10 수집 관리

- `GET /api/admin/sync/sources` — 출처별 `ExternalSyncState`: 마지막 상태, 마지막 성공 시각, 진행 날짜·페이지·커서, 오늘 호출량과 일일 한도, 실행 중 여부(`runningSince`), 마지막 오류. 처리 결과 건수(처리·검수·무시)는 `external_record`를 `synced_at` 기준으로 집계한다.
- `GET /api/admin/sync/retries` — `ExternalSyncRetry` 목록(시도 횟수, 마지막 오류)
- 수집기가 실제로 있는 출처는 `TOUR_API`뿐이다(`CULTURE_PORTAL`은 enum에만 있다). **[설계자 판단]** API는 `{source}` 경로를 유지하되, 수집기가 없는 출처를 요청하면 400을 돌려준다.
- `POST /api/admin/sync/{source}/run?mode=INCREMENTAL|FULL_CHECK` — 수동 실행. `INCREMENTAL`은 `sync()`, `FULL_CHECK`는 `fullCheck()`(누락 레코드 정리 포함)를 호출한다. 비동기로 시작하고 바로 202를 돌려준다. 기존 `tryLock`을 그대로 쓰므로 이미 실행 중이면 409를 돌려준다. 동시 실행 방지와 호출 한도는 기존 로직이 보장한다.
- `POST /api/admin/sync/{source}/pause`(사유 필수), `POST /api/admin/sync/{source}/resume`
  - `external_sync_state.admin_paused`(boolean)를 추가한다. 스케줄러와 수동 실행 모두 이 값을 보고 건너뛴다.
  - 기존 `SyncStatus.PAUSED`(호출 예산 소진으로 생기는 자동 일시정지)와는 다른 개념이므로 컬럼을 분리한다.
- `POST /api/admin/sync/retries/{id}/retry` — 실패 건 하나를 재처리한다. 기존 처리 경로(`ExternalRecordSyncService.apply`)를 타므로 `uk_external_record_source_id`와 기존 레코드 갱신 로직이 중복 생성을 막는다.
- **품질 점검(1차 최소):** `GET /api/admin/sync/quality` → 좌표 없음·이상(국내 범위 밖), 기간 누락 기간형 미션, 이미지 URL 없음, `missing_count > 0`, 장기 미갱신(`synced_at`이 N일 이전) 건수와 목록. 깨진 이미지를 실제 HTTP로 검사하는 기능은 2차다.

### 5.11 핵심 계측 (앱 이벤트 수집)

- 새 테이블 `app_event`: `app_event_id`, `event_id`(클라이언트가 만든 UUID, unique — 재전송 중복 방지), `member_id`, `family_id`, `event_name`, `properties`(JSONB), `platform`, `app_version`, `occurred_at`, `received_at`
- 앱 API `POST /api/v1/events` — 최대 100건 배치. 알 수 없는 `event_name`도 저장한다(앱 배포를 서버 배포에 묶지 않기 위해). 인증된 회원만 보낼 수 있다.
- **[결정]** 수집할 이벤트 목록은 나중에 정한다. 1차에서는 수집 API와 저장소만 만들고, 이벤트 이름과 properties는 검증하지 않는다. 이벤트 목록이 정해지면 끼리 픽 노출·클릭 성과와 대시보드 지표를 그 데이터에 연결한다.
- 서버에서 이미 기록되는 성공 이벤트(버킷 저장, 일정 생성·응답·완료, 리뷰)는 `app_event`에 중복으로 넣지 않는다. 기존 테이블을 기준으로 한다.
- 인덱스는 `(event_name, occurred_at)`. 보관 기간과 파티셔닝은 1차에서 하지 않는다. 데이터가 커지면 월 단위 파티셔닝으로 간다.

### 5.12 운영 대시보드 KPI

**공통 응답 형태** — KPI마다 다음을 준다.
`value`, `previousValue`(직전 같은 길이 기간), `numerator`, `denominator`, `sampleSize`, `trend`(최근 8주 주별 값), `formula`(산식 설명 문자열), `dataStatus`(`OK` / `NOT_COLLECTED` / `INSUFFICIENT_PERIOD` / `ERROR`), `computedAt`

- **목표값은 1차에서 뺀다.** **[설계자 판단]** 아직 목표 숫자가 없는데 화면에 "목표 미정"만 반복해서 보이면 정보가 없다. 1차 카드는 **현재 값 / 전주 대비 증감 / 8주 추이**만 보여준다.
  목표가 정해지면 2차에 `kpi_target` 테이블(`kpi_key`, `period_start`, `target_value`, `confirmed`)을 추가한다. 그때 카드에 "목표 대비 달성률"과 목표선(추이 차트의 가로선)을 붙인다. 응답에 `target` 필드를 추가하는 것뿐이라 1차 API와 호환된다.

- `dataStatus`로 "정상 0건"과 "미수집", "관찰 기간 미충족"을 구분한다(기획서 12절).
- 주는 KST 월요일 00:00부터 일요일 24:00까지다. **[설계자 판단]**
- 1차에는 사전 집계 테이블 없이 조회 시점에 계산하고, 결과를 10분 동안 메모리 캐시한다. **[설계자 판단]** 데이터 규모가 작아서 충분하다. 느려지면 일 단위 집계 테이블을 도입한다.

**1차 KPI 정의** (모두 테스트 회원·가족 제외)

| KPI | 산식 |
|---|---|
| 주간 활동 완료 가족 수 | 그 주에 `schedule.completed_at`이 있는 `COMPLETED` 일정을 가진 가족 수 |
| 주간 공동 활동 완료 일정 수 | 그 주에 `completed_at`인 `COMPLETED` 일정 수. "완료 = 참여자 1명의 유효 리뷰" 정책임을 `formula`에 명시 |
| 신규 연결 가족 수 | 두 번째 구성원의 `family_joined_at`이 그 주에 속하는 가족 수 (탈퇴 회원 포함 최초 시점 기준) |
| 주간 활성 가족 수 | 그 주에 질문 답변·버킷 저장·일정 생성/응답·리뷰 작성 중 하나라도 있는 가족 수 |
| 일정 확정률 | 그 주에 생성된 일정 중 `CONFIRMED` 또는 `COMPLETED`에 도달한 비율. 응답 마감 전 일정은 분모에서 제외 |
| 활동 완료율 | 일정일이 그 주인 확정 일정 중 `COMPLETED` 비율. 일정일 + 리뷰 작성 가능 기간이 지나지 않은 건은 분모에서 제외 |
| 다음 활동률 | 첫 완료일로부터 28일이 지난 가족 중 28일 안에 두 번째 완료가 있는 비율. 관찰 기간이 덜 찬 가족은 분모 제외. 표본 부족이면 `INSUFFICIENT_PERIOD` |
| 관계 경험 긍정 응답률 | 설문이 없으므로 `NOT_COLLECTED` (2차) |

- 완료 일정 수와 실제 현장 참여, 가족 전원의 만족도를 구분한다(기획서 3절). 완료 일정 KPI 상세에는 "리뷰를 쓴 참여자 수 / 수락한 참여자 수" 분포를 함께 준다.
- 운영 카드: 검수 대기 건수와 가장 오래 기다린 대기시간, 수집 오류(최근 실패 출처, 재시도 대기 건수), 운영 주의 항목(노출 중인데 미션이 비공개인 끼리 픽, 데이터 불일치 일정 수)
- "건의함" 카드는 고객지원이 2차라서 1차에는 없다.
- API: `GET /api/admin/dashboard/kpis?weekOf=`, `GET /api/admin/dashboard/kpis/{kpiKey}?weekOf=`(상세: 분자·분모 목록, 페이지네이션), `GET /api/admin/dashboard/ops`

### 5.13 API 계약과 프론트 연동

- springdoc `GroupedOpenApi`로 `admin` 그룹(`/api/admin/**`)을 분리한다. 프론트는 local/dev의 `/v3/api-docs/admin`에서 타입을 생성한다. 기존 `*ControllerDocs` 인터페이스 패턴을 관리자 컨트롤러에도 적용한다.
- 목록 API는 공통 페이지 응답(`page`, `size`, `totalElements`, `content`)을 쓴다. 기존에 같은 형태가 있으면 그것을 쓴다.
- 모든 시각은 KST offset이 붙은 ISO-8601로 응답한다.

## 6. 미결 사항

| 항목 | 내용 | 상태 |
|---|---|---|
| 앱 이벤트 목록 | 수집 API만 먼저 만들고 이벤트 정의는 나중에 | 나중에 결정 |
| KPI 목표값 | 1차 화면·API에서 제외, 2차에 `kpi_target`으로 추가 | 운영 결정 대기 |
| 리뷰 작성 가능 기간 | 활동 완료율 분모 제외 기준. 현재 정책값을 코드에서 확인해 사용 | 빌더가 확인 |

## 7. 구현 순서 [설계자 판단]

1. 관리자 인증 + 보안 체인 + 감사 로그 + 최초 관리자 생성
2. 테스트 플래그 + 기본 조회 (가장 단순하고 프론트 골격을 잡기 좋음)
3. 수집 관리
4. 미션 검수 (잠금 필드 포함)
5. 수동 등록 + 이미지 + 질문
6. 끼리 픽 (관리 + 앱 노출 + 버킷 출처)
7. 이벤트 수집 API
8. 대시보드 KPI

단계마다 별도 PR로 낸다. 각 단계에서 Flyway 마이그레이션은 V30부터 순서대로 붙인다.

## 8. 테스트 기준

- 보안: 앱 토큰 → 관리자 API 401, 관리자 토큰 → 앱 API 401, 비활성 관리자 토큰 → 401
- 검수: 탭 ②에서 숨긴 미션이 재수집 후에도 `HIDDEN` 유지, 다시 검수로 들어온 건에 "이전에 승인됨"과 이전 보완 값 노출, 종료·취소 미션 승인 시 상태 유지, 제외한 레코드가 원본이 바뀐 재수집에서도 `IGNORED` 유지, 동시 승인·동기화 충돌 시 409
- 잠금 필드: 잠긴 필드가 재수집 후 유지, 해제 후 원본으로 복귀
- 끼리 픽: 첫 페이지 상단 정렬, 필터 불일치 픽 미노출, 상단 픽과 일반 목록 중복 없음, 다음 페이지 커서 정상, 비공개 미션 미노출, 기간 밖 미노출, 하나의 완료가 하나의 픽에만 집계
- KPI: 테스트 가족 제외, 관찰 기간 미충족 시 분모 제외, 0건과 미수집 구분
- 감사 로그: 각 변경 API가 before/after와 사유를 남기고, 업무 변경이 롤백되면 로그도 롤백

## 9. 구현 메모 (2026-11-11)

구현하면서 설계와 달라졌거나 새로 정한 것. 모두 **[설계자 판단]**이다.

- **Swagger 문서:** 관리자 컨트롤러는 `*ControllerDocs` 인터페이스를 따로 두지 않고 컨트롤러에 `@Operation`을 직접 붙였다. 문서 그룹은 `app` / `admin` 두 개다(`/v3/api-docs/admin`).
- **잠금 필드:** `title_edited` / `checklist_edited` 컬럼은 지우지 않고 그대로 읽는다(`TITLE` / `CHECKLIST` 잠금과 같은 의미). 컬럼 정리용 2단계 마이그레이션은 하지 않았다.
- **검수 승인의 잠금:** 새 미션을 승인하면, 원본으로 만든 초기값(suggestion)과 다른 필드만 잠근다. 초기값을 만들 수 없는 원본이면 제목·소개·카테고리·유형·기간·체크리스트를 잠근다.
- **검수 승인 입력:** 운영자는 보완 값을 "원본 덮어쓰기"가 아니라 공개 가능한 미션 전체 폼(`MissionForm`)으로 보낸다. 검수 화면의 `suggestion`이 그 초기값이다.
- **활동 완료율 분모:** 리뷰 작성 기한이 코드에 없어서, 일정일로부터 7일이 지난 확정 일정만 분모에 넣는다(`KpiCalculator.COMPLETION_GRACE_DAYS`). 운영 정책이 정해지면 바꾼다.
- **KPI 기본 주:** `weekOf`가 없으면 직전에 끝난 주(KST 월~일)다. 진행 중인 주를 지난주와 비교하면 오해가 생겨서다.
- **신규 연결 가족:** 지금 가족에 남아 있는 구성원의 `family_joined_at` 기준이다. 탈퇴·이탈한 구성원 이력은 남지 않아 반영되지 않는다.
- **관리자 이미지:** `pending/admin/{adminId}/` 경로로 올리고, 확정하면 `public/content/`로 복사한다. 운영 CloudFront가 `public/` 경로를 서명 없이 열어 주는지 확인이 필요하다.
- **끼리 픽 성과:** 같은 미션을 다른 픽에서 다시 저장해도 버킷은 회원·미션당 하나이므로, 처음 저장한 픽의 출처가 유지된다. 픽 ID가 다른 미션의 것이면 출처 없이 저장한다.
- **첫 관리자 계정:** 자동 생성 기능은 만들지 않았다. pgcrypto를 쓰면 SQL로 바로 만들 수 있다.
  `INSERT INTO admin (login_id, password_hash, name) VALUES ('ops', crypt('<비밀번호>', gen_salt('bf', 10)), '운영자');`
