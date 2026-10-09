---
name: new-domain
description: 새 도메인이나 새 API를 추가할 때 사용. Entity부터 Controller, Swagger Docs, 에러/성공 코드, 마이그레이션, 테스트까지 KIRI 컨벤션대로 만드는 순서와 체크리스트.
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

**4. 코드**
- 형식 `{DOMAIN}{상태}_{번호}`. 이미 있는 코드와 번호가 겹치지 않게 enum 전체를 확인
- 성공 코드도 `exception/code/`에 둠 (term과 동일)

```java
NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION404_1", "존재하지 않는 질문입니다."),
```

**6. DTO**
- 요청은 `{Domain}ReqDTO` 안 record + 검증 어노테이션, 응답은 `{Domain}ResDTO` 안 `@Builder` record
- 요청과 응답 DTO를 같은 record로 재사용하지 않음

**7. Converter**
- `public class`, 생성자 없이 `public static` 메서드만. `builder()`는 여기서만 호출 (ArchitectureTest 검사)
- 메서드명은 `to{대상}` (`toInfo`, `toMemberTerm`)

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
