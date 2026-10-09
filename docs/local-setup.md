# 로컬 개발 환경

## docker-compose를 쓰는 이유

- dev 서버(RDS)와 같은 PostgreSQL 18을 사용
- PostgreSQL을 PC에 직접 설치하지 않아도 됨
- 다른 프로젝트의 DB와 섞이지 않음
- Flyway 마이그레이션 파일을 수정했을 때 로컬 DB를 한 줄로 비울 수 있음

> 직접 설치한 PostgreSQL 18을 써도 됩니다. `.env`의 `DB_URL`이 내 DB를 가리키기만 하면 됩니다.
> 단, 직접 설치한 PostgreSQL과 도커를 같이 쓰면 둘 다 5432 포트를 쓰려고 해서 엉뚱한 DB에 접속될 수 있으니 둘 중 하나만 실행하세요. ([도커가 아닌 DB에 접속되는 경우](#도커가-아닌-db에-접속되는-경우) 참고)

## 초기 설정

1. Docker Desktop 설치 후 실행 (왼쪽 아래 "Engine running" 확인)
2. 레포 루트에서 `.env.example`을 복사해 `.env` 작성

   ```properties
   DB_URL=jdbc:postgresql://localhost:5432/kiri
   DB_USER=<원하는 계정명>
   DB_PW=<원하는 비밀번호>
   JWT_SECRET=<32바이트 이상 임의 문자열>
   ```

   - 앱은 `application.yaml`의 `spring.config.import`로 레포 루트의 `.env`를 읽습니다.
   - docker-compose도 같은 `.env`의 `DB_USER`, `DB_PW`로 DB 계정을 만듭니다.

3. DB 실행

   ```bash
   docker compose up -d
   ```

   - `-d` 없이 실행하면 로그를 계속 출력하는 모드라서, Ctrl + C를 누르면 DB도 같이 중지됩니다. 평소에는 `-d`를 붙여 백그라운드로 실행하세요.

4. 앱 실행 후 `http://localhost:8080/actuator/health`가 `UP`이면 연결 완료

> `./gradlew test`로 전체 테스트를 돌릴 때도 DB가 실행 중이어야 합니다. (`KiriServerApplicationTests`가 실제 DB에 접속)

## 자주 쓰는 명령어

모두 레포 루트(`docker-compose.yml`이 있는 위치)에서 실행합니다.

```bash
# DB 실행 (-d: 백그라운드 실행)
docker compose up -d

# DB 중지 (데이터 유지)
docker compose down

# DB 중지 + 데이터 삭제 (초기화)
docker compose down -v

# 실행 상태 확인
docker compose ps

# DB 로그 보기 (Ctrl + C로 종료, DB는 계속 실행됨)
docker compose logs -f postgres

# DB 재시작
docker compose restart

# DB에 직접 접속해서 SQL 실행 (\q로 종료, -U에는 .env의 DB_USER 값)
docker compose exec postgres psql -U <DB_USER> -d kiri
```

## 초기화는 언제 하나요?

- Flyway는 이미 적용한 마이그레이션 파일이 수정되면 실행을 거부합니다. (`checksum mismatch` 에러)
- dev에 아직 반영되지 않은 마이그레이션 파일을 수정했다면, 로컬 DB를 초기화하고 다시 실행하세요.

```bash
docker compose down -v
docker compose up -d
```

> dev에 이미 반영된 마이그레이션 파일은 수정하지 말고 다음 버전 파일을 새로 추가하세요. PR에서 CI가 검사합니다.

## Claude Code hooks

`.claude/settings.json`에 아래 hook이 설정되어 있습니다.

| 시점 | 동작 |
|---|---|
| Java 파일 수정 직후 | 수정한 파일만 Spotless 포맷 |
| 응답을 마치기 전 | Java 파일이나 `build.gradle`을 바꿨다면 `spotlessCheck` + `ArchitectureTest` 실행, 실패하면 Claude가 이어서 수정 |

- JDK 25는 `JAVA_HOME` → PATH → (Mac) `java_home` → IntelliJ가 받은 JDK(`~/.jdks`) 순으로 자동으로 찾습니다. `[hook] JDK 25를 찾지 못해...`가 보이면 `JAVA_HOME`을 JDK 25 경로로 설정하세요.
- 개인 설정은 `.claude/settings.local.json`에 둡니다. (git에 올라가지 않음)

## 문제와 해결 방법

### `failed to connect to the docker API`

Docker Desktop이 꺼져 있습니다. 실행 후 다시 시도하세요.

### `port is already allocated` (5432)

- PC에서 다른 PostgreSQL이 5432 포트를 쓰고 있습니다. 아래 [도커가 아닌 DB에 접속되는 경우](#도커가-아닌-db에-접속되는-경우)를 참고해서 PC의 PostgreSQL을 중지하세요.
- PC의 PostgreSQL을 계속 써야 한다면 `docker-compose.yml`의 포트를 `"127.0.0.1:5433:5432"`로 바꾸고 `.env`의 `DB_URL`도 5433으로 변경하세요.

### 도커가 아닌 DB에 접속되는 경우

Windows에 PostgreSQL을 직접 설치했다면 서비스가 자동으로 실행되어 5432 포트를 먼저 잡고 있을 수 있습니다. 이 경우 에러 없이 `localhost:5432`가 도커 대신 PC의 PostgreSQL로 연결됩니다. 테이블이 예상과 다르거나, 설정을 바꿨는데 반영이 안 되면 의심해 보세요.

**확인 방법**

1. 포트를 쓰는 프로세스 확인

   ```powershell
   netstat -ano | findstr :5432
   ```

   - 도커는 `127.0.0.1:5432`로만 LISTENING
   - `0.0.0.0:5432`로 LISTENING 중인 프로세스가 있으면 PC에 설치한 PostgreSQL일 가능성이 높음

2. 접속한 DB에서 `SELECT version();` 실행

   | 결과 | 접속한 DB |
   |---|---|
   | `(Debian ...) on x86_64-pc-linux-gnu` | 도커 |
   | `on x86_64-windows, compiled by msvc` | PC에 설치한 PostgreSQL |

**해결 방법**

관리자 PowerShell에서 PC의 PostgreSQL 서비스를 중지하고, 재부팅 후 자동 실행되지 않게 변경합니다. 서비스 이름은 설치한 버전에 따라 다르니 `Get-Service postgresql*`로 먼저 확인하세요.

```powershell
Stop-Service postgresql-x64-18
Set-Service postgresql-x64-18 -StartupType Manual
```

### 컨테이너가 바로 종료되는 경우 (`docker compose ps`에 `Exited (1)`)

- `docker compose logs postgres`에 `in 18+, these Docker images are configured to store database data in a format...`가 보이면, 볼륨에 이전 버전(PostgreSQL 17 등) 데이터가 남아 있는 것입니다.
- PostgreSQL 18 이미지는 데이터 저장 폴더 구조가 바뀌어서 이전 버전 데이터로는 시작하지 않습니다.
- 로컬 데이터를 지워도 되면 볼륨을 새로 만드세요.

```bash
docker compose down -v
docker compose up -d
```

### 앱 실행 시 DB 접속 실패

- `.env`의 `DB_USER`, `DB_PW`, `DB_URL` 값을 확인하세요.
- DB 계정은 볼륨을 처음 만들 때만 설정됩니다. `.env`의 계정 정보를 바꿨다면 `docker compose down -v` 후 다시 실행하세요.
- 위 [도커가 아닌 DB에 접속되는 경우](#도커가-아닌-db에-접속되는-경우)도 확인하세요.

### DataGrip, IntelliJ에서 `SHOW timezone`이 UTC로 나오는 경우

DB 설정 문제가 아니라 DataGrip, IntelliJ가 접속할 때 세션 타임존을 UTC로 덮어쓰기 때문입니다. 데이터소스 Properties → Advanced → VM options에 `-Duser.timezone=Asia/Seoul`을 입력하고 다시 연결하세요.
