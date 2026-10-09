---
name: write-tests
description: 테스트 코드를 작성하거나 수정할 때 사용. Entity·Converter 단위 테스트, Service Mockito 테스트, Controller WebMvcTest의 KIRI 작성 패턴과 DisplayName 규칙.
---

# 테스트 작성

테스트 위치는 main과 같은 패키지(`src/test/java/com/kiri/server/domain/{domain}/...`)입니다. 레이어마다 아래 참고 파일을 먼저 열어보고 같은 모양으로 작성합니다.

## 공통 규칙

- `@DisplayName`은 한국어 문장. 조건과 결과가 드러나게, 마침표로 끝냄
  - 좋음: `"필수 약관에 동의하지 않으면 TERM400_1 예외가 발생한다."`
  - 나쁨: `"agree 테스트"`, `"실패 케이스"`
- 메서드명은 `{대상메서드}_{상황}`: `agree_requiredNotAgreed`, `getTerms_success`
- 검증은 AssertJ (`assertThat`, `assertThatThrownBy`). JUnit `assertEquals` 쓰지 않음
- given / when / then 순서로 작성하되 주석은 생략해도 됨
- 성공 케이스와 실패 케이스(예외, 검증 실패, 인증 실패)를 모두 작성
- 테스트용 객체 생성에는 `builder()`를 써도 됨 (ArchitectureTest는 main 코드만 검사)

## Entity / Converter: 순수 단위 테스트

참고: `term/entity/MemberTermTest.java`, `term/converter/TermConverterTest.java`

- Spring, Mockito 없이 객체를 직접 만들어 메서드 결과를 검증
- 엔티티 비즈니스 메서드(상태 변경)는 경계값을 포함해 테스트

## Service: Mockito

참고: `term/service/TermServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private QuestionService questionService;

    @Test
    @DisplayName("존재하지 않는 질문이면 QUESTION404_1 예외가 발생한다.")
    void getQuestion_notFound() {
        given(questionRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.getQuestion(1L))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.NOT_FOUND);
    }
}
```

- stub은 BDDMockito `given(...).willReturn(...)`, `willThrow(...).given(...)`
- 호출 여부는 `verify(...)`, 호출되면 안 되면 `verify(..., never())`
- 예외는 타입과 `errorCode`까지 검증

## Controller: @WebMvcTest

참고: `term/controller/TermControllerTest.java`

- `@WebMvcTest({Domain}Controller.class)` + Service는 `@MockitoBean`
- 인증이 필요한 API는 term과 같이 `@Import`로 `SecurityConfig`, `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`, `JwtProvider`를 가져오고 `@TestPropertySource`로 `jwt.secret` 지정, `jwtProvider.createAccessToken(MEMBER_ID)`로 토큰 생성
- 응답은 `jsonPath("$.isSuccess")`, `jsonPath("$.code")`, `jsonPath("$.result...")`로 공통 응답 포맷까지 검증
- API마다 최소: 성공 1개 + 토큰 없음(401) 1개 + 요청 검증 실패나 도메인 예외 1개

## 실행

```bash
./gradlew test --tests "com.kiri.server.domain.{domain}.*"
./gradlew test --tests "com.kiri.server.domain.{domain}.service.{Domain}ServiceTest"
```

- 실패하면 테스트를 지우거나 기대값을 바꿔서 통과시키지 말고, main 코드와 테스트 중 어느 쪽이 맞는지 먼저 판단
- 판단이 어려우면 실패 내용과 함께 사람에게 물어보기
