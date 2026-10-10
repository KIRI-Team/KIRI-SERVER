---
name: new-domain
description: 새 도메인이나 새 API를 추가할 때, 또는 엔티티·Repository·DTO·Converter를 작성·수정할 때 사용. Entity부터 Controller, Swagger Docs, 에러/성공 코드, 마이그레이션, 테스트까지 KIRI 컨벤션대로 만드는 순서와 체크리스트.
---

# 새 도메인 / API 추가

`domain/term`이 참고 구현입니다. 각 단계에서 term의 같은 파일을 먼저 열어보고 같은 모양으로 작성합니다.

## 순서

아래 순서대로 만들면 컴파일 에러 없이 단계별로 진행할 수 있습니다. `{domain}`은 소문자(`question`), `{Domain}`은 PascalCase(`Question`), `{DOMAIN}`은 대문자(`QUESTION`)입니다.

| # | 파일 | 참고 |
|---|---|---|
| 1 | `entity/{Entity}.java` | `term/entity/MemberTerm.java` |
| 2 | `db/migration/V{n}__{설명}.sql` | `flyway-migration` 스킬 |
| 3 | `repository/{Entity}Repository.java` | `term/repository/MemberTermRepository.java` |
| 4 | `exception/code/{Domain}ErrorCode.java`, `{Domain}SuccessCode.java` | `term/exception/code/` |
| 5 | `exception/{Domain}Exception.java` | `term/exception/TermException.java` |
| 6 | `dto/{Domain}ReqDTO.java`, `{Domain}ResDTO.java` | `term/dto/` |
| 7 | `converter/{Domain}Converter.java` | `term/converter/TermConverter.java` |
| 8 | `service/{Domain}Service.java` | `term/service/TermService.java` |
| 9 | `controller/docs/{Domain}ControllerDocs.java` | `term/controller/docs/TermControllerDocs.java` |
| 10 | `controller/{Domain}Controller.java` | `term/controller/TermController.java` |
| 11 | 테스트 | `write-tests` 스킬 |

## 단계별 주의점

**1. Entity**
- `BaseEntity` 상속, `@Table(name = "snake_case")`, ID는 `@Column(name = "{entity}_id")`
- 모든 컬럼에 `@Column(name = ...)`을 명시해서 마이그레이션 SQL과 1:1로 맞춤
- 연관관계는 `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = ..., nullable = ...)`
- 상태 변경은 setter 대신 메서드로: `updateAgreement(...)`, `completeOnboarding()`
- 유니크 제약
  - 이름은 `uk_{테이블}_{설명}`. PostgreSQL은 제약 이름이 스키마 전체에서 유일해야 하므로 테이블명 포함
  - `columnNames`는 필드명이 아닌 DB 컬럼명을 원소별 문자열로: `{"member_id", "term_id"}`. 틀리면 앱 기동 실패
  - 제약 위에 무엇을 막는지 한 줄 주석 (`// 한 가족은 하루에 질문 하나만 배정`)
- 컬럼 주석은 이름만으로 드러나지 않는 것(null의 의미, 값 범위·단위, 비즈니스 의미)이 있을 때 필수
  - `// 고정 노출 일차 (1~7, null: 일반 질문)`, `// 동의 시점의 약관 버전`
  - `// 닉네임`처럼 이름을 반복하는 주석은 강제하지 않음

**3. Repository**
- 없을 수 있는 단건 조회는 `Optional<Entity>` 반환. 엔티티를 그대로 반환하면 없을 때 null이 와서 `orElseThrow`를 쓸 수 없음
- 목록을 돌며 LAZY 연관관계를 읽는 화면이면 fetch join 메서드를 둠 (`@Query("... JOIN FETCH a.member ...")`). 프록시의 `getId()`는 쿼리 없음

**4. 코드**
- 형식 `{DOMAIN}{상태}_{번호}`. 이미 있는 코드와 번호가 겹치지 않게 enum 전체를 확인
- 성공 코드도 `exception/code/`에 둠 (term과 동일)

```java
NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION404_1", "존재하지 않는 질문입니다."),
```

**6. DTO**
- 요청은 `{Domain}ReqDTO` 안 record + 검증 어노테이션, 응답은 `{Domain}ResDTO` 안 `@Builder` record
- 요청과 응답 DTO를 같은 record로 재사용하지 않음
- 사용자의 정상 상태(예: 가족 미소속)는 에러가 아니라 응답 필드로 표현 (`hasFamily: false`). 에러는 잘못된 요청이나 데이터 누락일 때만

**7. Converter**
- `public class`, 생성자 없이 `public static` 메서드만. `builder()`는 여기서만 호출 (ArchitectureTest 검사)
- 메서드명은 `to{대상}` (`toInfo`, `toMemberTerm`)
- 같은 데이터를 두 경로로 넘기지 않음. 연관 엔티티 값은 받은 엔티티에서 꺼냄 (`toTodayQuestion(familyQuestion, ...)` 안에서 `familyQuestion.getQuestion().getContent()`)
  - 한 단계 체이닝은 허용. 체인이 길거나, 중간에 null이 가능하거나, 꺼낸 값으로 판단하면 엔티티 메서드로 감쌈 (`familyQuestion.belongsTo(family)`)

**8. Service**
- `@Service` + `@RequiredArgsConstructor`, 메서드마다 `@Transactional` / `@Transactional(readOnly = true)`
- 없는 데이터는 `orElseThrow(() -> new {Domain}Exception({Domain}ErrorCode.NOT_FOUND))`
- 외부 API 호출이 있으면 AGENTS.md의 "Service / 트랜잭션" 규칙을 따름

**9~10. Controller + Docs**
- 경로는 `/api/v1/{복수형 리소스}` (`/api/v1/terms`)
- Swagger 어노테이션은 Docs 인터페이스에만. 응답 예시 JSON에 실제 성공/에러 코드를 씀
- Controller는 `BaseSuccessCode code = ...; return ApiResponse.onSuccess(code, result);` 모양 유지
- 인증 없이 열어야 하는 API면 `SecurityConfig.PUBLIC_URLS` 수정이 필요하므로 먼저 물어보기

## 마무리 체크리스트

- [ ] `./gradlew spotlessApply`
- [ ] `./gradlew test --tests "com.kiri.server.ArchitectureTest"` 통과
- [ ] 새 도메인 테스트 통과 (`./gradlew test --tests "com.kiri.server.domain.{domain}.*"`)
- [ ] 마이그레이션 파일 추가 (DB를 띄울 수 있으면 전체 `./gradlew test`로 `ddl-auto: validate` 통과 확인)
- [ ] 커밋을 목적별로 나눔 (엔티티+마이그레이션+Repository / API / 테스트)
