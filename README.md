# KIRI-SERVER
KIRI 서버 레포입니다.

## 로컬 테스트 실행

1. `.env.example`을 `.env`로 복사하고 DB 값만 채웁니다. 나머지 값은 `test` 프로필의 더미 설정(`src/test/resources/application-test.yaml`)을 사용합니다.
   - `DB_URL=jdbc:postgresql://localhost:5432/kiri`, `DB_USER`·`DB_PW`는 임의 값 (docker compose가 같은 값을 사용)
2. `docker compose up -d`로 PostgreSQL을 띄웁니다.
3. `./gradlew test`를 실행합니다. `test` 프로필은 `build.gradle`에서 자동으로 켜집니다.
   - IntelliJ에서 실행할 때는 테스트 러너를 Gradle로 두어야 `test` 프로필이 적용됩니다.

## 문서

- [배포 및 롤백](docs/deploy.md)
- [인프라 (Terraform)](infra/README.md)
