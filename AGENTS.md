# AGENTS.md

KIRI 서버 레포에서 작업하는 AI 에이전트용 규칙입니다. 사람이 읽어도 되도록 팀 컨벤션을 요약했습니다.

## 기술 스택

- Java 25, Spring Boot 4.1, Gradle (Maven 사용 금지)
- Spring Data JPA + PostgreSQL 18, Flyway (`ddl-auto: validate`)
- Spring Security + JWT, springdoc(Swagger), Lombok
- 버전은 `build.gradle`이 기준이며, 추측해서 쓰지 않습니다.
- Spring Boot 4에서 바뀐 설정 키·패키지가 있으므로, 기억에 의존하지 말고 `application*.yaml`과 기존 코드를 먼저 확인합니다.

## 명령어

```bash
./gradlew spotlessApply                                  # 포맷 자동 수정
./gradlew spotlessCheck                                  # 포맷 검사 (CI와 동일)
./gradlew test --tests "com.kiri.server.ArchitectureTest" # 구조 규칙 검사
./gradlew test --tests "com.kiri.server.domain.term.*"     # 특정 패키지 테스트
./gradlew test                                           # 전체 테스트 (로컬 DB 필요)
docker compose up -d                                     # 로컬 DB 실행
```

전체 테스트의 `KiriServerApplicationTests`는 실제 DB에 접속합니다. 로컬 DB와 `.env` 설정은 [docs/local-setup.md](docs/local-setup.md)를 참고합니다.

## 패키지 구조

```
domain/{도메인}/
├── controller/       # 요청 진입점, ApiResponse로 감싸서 반환만
│   └── docs/         # Swagger 명세 인터페이스 ({Domain}ControllerDocs)
├── service/          # 비즈니스 로직, 트랜잭션
├── repository/       # JpaRepository
├── dto/              # {Domain}ReqDTO, {Domain}ResDTO (inner record)
├── converter/        # Entity <-> DTO 변환 static 메서드
├── entity/
└── exception/
    ├── code/         # {Domain}ErrorCode, {Domain}SuccessCode
    └── {Domain}Exception.java
global/               # 공통 설정, 응답 포맷, 예외 처리, 보안, 로깅
```

새 코드를 쓸 때는 `domain/term`을 참고 구현으로 삼아 같은 모양으로 작성합니다.

## 코드 규칙

`ArchitectureTest`와 `spotlessCheck`가 검사하는 규칙은 (검사)로 표시했습니다.

**엔티티**
- `BaseEntity` 상속 (검사), `@Setter` 금지 (검사). 상태 변경은 의미 있는 메서드로 (`MemberTerm.updateAgreement`)
- `@NoArgsConstructor(access = PROTECTED)`, `@Builder` + `@AllArgsConstructor(access = PRIVATE)`
- ID 필드명은 `id`, 컬럼명은 `@Column(name = "{엔티티}_id")`. 테이블·컬럼명은 snake_case로 명시
- 연관관계는 `fetch = FetchType.LAZY` (검사), enum은 `@Enumerated(EnumType.STRING)` (검사)
- 유니크 제약은 이름을 붙임 (`uk_{테이블}_{설명}`)
- `@Data`, `@ToString`, `@EqualsAndHashCode` 금지 (지연 로딩 연관관계를 건드려 쿼리·순환 참조 발생)

**DTO / Converter**
- DTO는 `{Domain}ReqDTO`, `{Domain}ResDTO` 안의 record로 묶음. 응답 record에는 `@Builder`
- 요청 DTO에 `@NotNull`, `@NotBlank` 등 검증 + 컨트롤러에서 `@Valid`
- `builder()` 호출은 Converter에서만 (검사). Converter는 Bean이 아닌 static 메서드 클래스
- 엔티티를 응답으로 직접 반환하지 않고 항상 DTO로 변환

**Controller**
- `@RestController` + `@RequiredArgsConstructor`, `{Domain}ControllerDocs`를 구현
- Swagger 어노테이션(`@Operation`, `@ApiResponses` 등)은 Docs 인터페이스에만 작성
- 비즈니스 로직 없이 Service 호출 후 `ApiResponse.onSuccess(code, result)` 반환
- Repository 직접 호출 금지 (검사). 로그인 회원은 `@AuthenticationPrincipal Long memberId`
- API 요청·응답이나 에러 코드가 바뀌면 Docs 인터페이스의 설명과 예시 JSON도 함께 수정

**Service / 트랜잭션**
- 의존성은 생성자 주입만 (`@RequiredArgsConstructor` + `private final`). 필드 `@Autowired` 금지
- DB를 쓰는 메서드는 `@Transactional`, 조회만 하면 `@Transactional(readOnly = true)`
- 외부 API 호출은 트랜잭션 밖에서 합니다 (검사). 외부 응답을 기다리는 동안 DB 커넥션을 점유하지 않기 위함
  - 외부 연동 클래스는 `client` 패키지에 둠 (예: `domain/auth/client/KakaoClient`). `@Transactional` 메서드에서 `..client..` 패키지(RestClient, WebClient 포함) 호출 시 실패
  - 로그인처럼 외부 검증 후 DB에 저장하는 흐름은 트랜잭션 없는 메서드에서 외부 호출 → `@Transactional` Service 호출 순서로
  - 간단하면 트랜잭션 없는 메서드에서 조회 → 외부 호출 → 저장 (이때 dirty checking이 안 되므로 `save()` 명시)
  - 여러 쓰기를 묶어야 하면 `TransactionTemplate`으로 DB 구간만 감싸거나, 외부 호출용 클래스와 `@Transactional` Service를 분리
  - 커밋 후에 해도 되는 호출(알림 등)은 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`
  - 같은 클래스 안에서 `@Transactional` 메서드를 호출하면 프록시를 거치지 않아 트랜잭션이 적용되지 않음
- 반복문 안에서 Repository를 호출하지 않음. `findAllById`, `findAllBy...In` 등으로 한 번에 조회
- 목록 조회는 N+1을 확인하고 필요하면 fetch join / `@EntityGraph`. 커질 수 있는 목록은 `Pageable`
- 동시 수정이 문제 되는 로직은 Repository에 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 등을 명시. 방식이 애매하면 먼저 물어보기

**예외 / 응답 코드**
- 비즈니스 예외는 `{Domain}Exception extends ProjectException`에 `{Domain}ErrorCode`를 담아 던짐
- 코드 형식: `{DOMAIN}{HTTP상태}_{번호}` (예: `TERM404_1`, `TERM200_1`). 같은 도메인·상태 안에서 번호를 이어서 부여
- 조회 실패는 `orElseThrow(() -> new {Domain}Exception(...NOT_FOUND))`로 도메인 예외를 던짐
- 유니크 제약 위반처럼 예상 가능한 DB 예외는 Service에서 `DataIntegrityViolationException`을 잡아 도메인 에러 코드로 변환 (전역 409 처리는 안전망)
- 에러 응답은 `GeneralExceptionAdvice`가 처리하므로 컨트롤러에서 try-catch로 응답을 만들지 않음

**기타**
- `jakarta.persistence`, `jakarta.validation` 사용 (`javax.*` 금지, 단 `javax.crypto` 등 JDK 패키지는 허용)
- 로그: 4xx는 `log.warn`, 예상치 못한 5xx만 `log.error`. 토큰, 비밀번호, 이메일 등 개인정보는 로그에 남기지 않음. `System.out` 금지
- 시간은 `LocalDateTime`, 서버·DB 모두 `Asia/Seoul` 기준
- 와일드카드 import(`import jakarta.persistence.*;`) 금지
- 주석은 한국어로, "왜"가 코드만으로 드러나지 않는 곳에만 짧게

## DB 마이그레이션

- 엔티티를 추가·변경하면 `src/main/resources/db/migration/V{다음 번호}__{설명}.sql`을 반드시 함께 작성
- 작성 전 develop 기준 가장 큰 버전 번호를 확인 (팀원과 번호 충돌 방지)
- 이미 커밋된 `V*.sql`은 수정·삭제·이름 변경 금지 (CI 검사). 바꿀 게 있으면 다음 버전 파일을 추가
- 데이터 이관 / 제약 추가 / 테이블·컬럼 삭제 중 2개 이상이 필요하면 버전을 나눠 작성 (이관 → 제약 → 삭제 순)
- `ALTER TABLE`의 `ADD`/`DROP` 절에 `IF EXISTS`/`IF NOT EXISTS`를 쓰지 않음 (`CREATE TABLE IF NOT EXISTS`, `DROP TABLE IF EXISTS`는 허용)

## 테스트

| 대상 | 방식 | 참고 |
|---|---|---|
| Entity, Converter, 유틸 | 순수 단위 테스트 (Spring 없음) | `MemberTermTest`, `TermConverterTest` |
| Service | `@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks`, BDDMockito `given` | `TermServiceTest` |
| Controller | `@WebMvcTest` + `@MockitoBean`, MockMvc `jsonPath`로 응답 코드 검증 | `TermControllerTest` |

- 검증은 AssertJ (`assertThat`, `assertThatThrownBy`)
- `@DisplayName`은 한국어 문장으로, 상황과 결과가 드러나게: `"존재하지 않는 회원이면 MEMBER404_1 예외가 발생한다."`
- 메서드명은 `{대상}_{상황}` (예: `agree_memberNotFound`)
- 새 기능은 Service 테스트 필수, API마다 Controller 테스트 1개 이상, 엔티티 비즈니스 메서드는 단위 테스트
- 성공 케이스와 실패 케이스(예외, 검증 실패, 인증 실패)를 모두 작성
- 동작을 바꿨다면 관련 테스트도 함께 수정·추가

## Git

- 브랜치: develop에서 `{type}/#{이슈번호}-{kebab-case 설명}` (예: `feat/#12-user-signup`)
- 커밋: `{type}: {한국어 설명}`. type은 `feat`, `fix`, `refactor`, `docs`, `chore`, `init`, `test`
- 커밋은 하나의 목적만 갖는 단위로 나눔 (예: 엔티티+Repository / API 구현 / 테스트)
- PR: 제목 `[type] 요약`, `.github/PULL_REQUEST_TEMPLATE.md` 사용, 본문에 `Closes #{이슈번호}`

## 완료 조건

작업을 끝났다고 보고하기 전에 모두 확인합니다.

- [ ] `./gradlew spotlessApply` 후 `spotlessCheck` 통과
- [ ] `ArchitectureTest`와 관련 테스트 통과 (DB를 띄울 수 있으면 전체 `./gradlew test`)
- [ ] 엔티티를 바꿨다면 마이그레이션 파일 추가
- [ ] 새 API라면 Docs 인터페이스, SuccessCode/ErrorCode, Controller·Service 테스트까지 작성
- [ ] 실행하지 못한 검사가 있으면 그 사실과 이유를 함께 보고

## 작업 경계

**항상**
- 기존 코드 패턴을 먼저 찾아보고 같은 방식으로 작성
- 요청 범위 안에서 최소한으로 변경. 관련 없는 리팩토링·포맷 변경을 섞지 않음
- 에러는 원인을 찾아 고침. 임시 우회(예외 삼키기, 검증 끄기)로 넘어가지 않음

**먼저 물어보기**
- 의존성 추가·버전 변경 (`build.gradle`)
- 공통 설정 변경: `global/` (Security, 예외 처리, 응답 포맷), `application*.yaml`, `.github/workflows/`
- 기존 테이블·컬럼 삭제나 데이터 이관이 필요한 마이그레이션
- PR 생성, 원격 push
- 도메인 용어나 비즈니스 규칙의 의미가 코드만으로 확실하지 않을 때 (추측해서 구현하지 않음)

**금지**
- main/develop에 직접 커밋·push, PR 머지
- `.env`, 키 파일(`*.pem` 등) 읽기·커밋, 비밀값을 코드나 로그에 쓰기
- 로컬이 아닌 DB(dev/prod)에 접속하거나 명령 실행
- 테스트를 통과시키려고 테스트나 `ArchitectureTest` 규칙을 지우거나 약화하기

## 스킬

작업 종류별 상세 절차는 스킬로 분리했습니다. 해당 작업을 시작할 때 읽습니다.

| 스킬 | 언제 |
|---|---|
| [new-domain](.agents/skills/new-domain/SKILL.md) | 새 도메인·API 추가 |
| [flyway-migration](.agents/skills/flyway-migration/SKILL.md) | 엔티티 변경으로 스키마를 바꿀 때 |
| [write-tests](.agents/skills/write-tests/SKILL.md) | 테스트 작성·수정 |

원본은 `.agents/skills/`(Codex가 읽는 위치)에 두고, `.claude/skills/`에는 원본을 가리키는 파일만 둡니다. 스킬을 고칠 때는 원본만 수정하고, `description`을 바꿨다면 `.claude/skills/`의 같은 파일도 맞춥니다.

## 이 문서 관리

- 같은 실수를 반복하거나 새 컨벤션이 정해지면 이 문서에 규칙을 추가. 에이전트는 수정안을 제안만 하고 반영은 사람이 확인 후 진행
- 이 문서는 200줄 안쪽으로 유지. 길어지는 절차는 `docs/`나 스킬로 분리하고 여기서는 링크만
