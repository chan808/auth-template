# Auth Template

인증과 회원 관리를 매번 처음부터 다시 만들지 않기 위해 만든 풀스택 템플릿입니다.  
단순 로그인 예제가 아니라, 실제 서비스에서 자주 마주치는 인증 보안 이슈와 운영 포인트를 처음부터 포함하는 것을 목표로 구성했습니다.

백엔드는 `Spring Boot + Spring Security + JPA + Redis`, 프론트엔드는 `Next.js + React + TypeScript` 기반입니다.  
회원가입, 로그인, 이메일 인증, 비밀번호 재설정, OAuth2 로그인, 세션 무효화, Rate Limiting, 관측성까지 한 번에 시작할 수 있습니다.

## 핵심 포인트

- `JWT Access Token + HttpOnly Refresh Token` 구조
- Redis 기반 세션 인덱스와 Refresh Token 저장소
- `/reissue` 동시 요청 충돌 방지
- 로그인, 회원가입, 비밀번호 재설정 Rate Limiting
- OAuth2 로그인과 일반 로그인 정책 분리
- 비밀번호 변경/회원 탈퇴 후 전체 세션 무효화
- Spring Boot Actuator + Micrometer + Prometheus + Grafana 관측 환경
- WebMvcTest, 단위 테스트, Testcontainers 기반 통합 테스트 분리
- Next.js 프론트와 분리된 구조로 실제 서비스에 가까운 흐름 구성

## 기술 스택

### Backend

- Kotlin
- JDK 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- MySQL
- Redis
- Flyway
- OAuth2 Client
- Spring Modulith
- Micrometer
- Prometheus
- MockK
- Testcontainers
- ArchUnit

### Frontend

- Next.js 16
- React 19
- TypeScript
- next-intl
- TanStack Query
- Zustand
- Axios
- Tailwind CSS 4
- shadcn/ui

### Infra

- Docker Compose
- Nginx reverse proxy config
- Prometheus
- Grafana

## 아키텍처 요약

### 인증 흐름

- Access Token은 프론트 메모리에만 저장합니다.
- Refresh Token은 `HttpOnly` 쿠키로만 저장합니다.
- Refresh Token 본문은 서버에 평문으로 저장하지 않고 해시만 저장합니다.
- Redis에는 `RT:{sid}`와 회원별 세션 인덱스를 함께 저장해 단일 세션 무효화와 전체 세션 무효화를 모두 처리합니다.

### 보안 설계

- 기본 클라이언트 IP는 `remoteAddr`를 사용하고, 신뢰 프록시 설정이 있을 때만 forwarded header를 해석합니다.
- 로그인, 회원가입, 비밀번호 재설정 요청은 Redis Lua 기반 Rate Limiting으로 보호합니다.
- OAuth 계정은 로컬 비밀번호 재설정 대상에서 제외해 정책 충돌을 막았습니다.
- 비밀번호 변경과 회원 탈퇴 후 세션 무효화는 `AFTER_COMMIT` 이벤트로 처리해 DB 롤백과 Redis 상태가 어긋나지 않도록 했습니다.
- JWT secret이 없으면 애플리케이션이 시작되지 않도록 fail-fast로 구성했습니다.

### 관측성

- `/actuator/health`, `/actuator/info`, `/actuator/prometheus`만 외부 노출합니다.
- HTTP 요청 수, 응답 시간 히스토그램, JVM/HikariCP 메트릭을 기본 수집합니다.
- 로그인 성공/실패, 토큰 재발급 실패 원인, 비밀번호 재설정, 회원가입, 세션 무효화 같은 도메인 이벤트를 커스텀 메트릭으로 기록합니다.
- Grafana 대시보드와 Prometheus datasource를 provisioning으로 자동 구성합니다.

## 디렉터리 구조

```text
auth-template/
├─ backend/
│  ├─ src/main/kotlin/.../auth
│  ├─ src/main/kotlin/.../member
│  ├─ src/main/kotlin/.../common
│  ├─ src/main/resources/db/migration
│  ├─ monitoring/
│  ├─ docker-compose.yml
│  ├─ .env.example
│  └─ HELP.md
├─ frontend/
│  ├─ src/app
│  ├─ src/features
│  ├─ src/shared
│  ├─ messages
│  └─ .env.example
├─ infra/
│  └─ nginx/
├─ 이력서를 위한 정보.md
└─ 포트폴리오를 위한 정보.md
```

## 주요 기능

### 회원

- 이메일 기반 회원가입
- 이메일 인증 완료 후 로그인 허용
- 내 정보 조회
- 프로필 수정
- 비밀번호 변경
- 회원 탈퇴

### 인증

- 이메일/비밀번호 로그인
- Access Token 재발급
- 로그아웃
- 비밀번호 재설정 메일 발송
- 비밀번호 재설정 완료

### OAuth2

- Google 로그인
- OAuth 계정과 일반 계정의 인증 정책 분리
- 프론트 콜백 페이지에서 백엔드와 토큰 교환

## 로컬 실행

### 1. 인프라 실행

`backend/.env.example`을 `backend/.env`로 복사한 뒤 값을 채웁니다.

```bash
cd backend
docker compose up -d mysql redis minio
```

관측 스택까지 함께 띄우려면:

```bash
docker compose --profile observability up -d prometheus grafana
```

### 2. 백엔드 실행

`backend/src/main/resources/application-local.example.yml`을 `application-local.yml`로 복사한 뒤 메일/OAuth 값을 채웁니다.

필수 설정:

- `JWT_SECRET`
- 메일 계정 정보
- 필요 시 OAuth client 정보

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 3. 프론트 실행

`frontend/.env.example`을 `frontend/.env.local`로 복사합니다.

```bash
cd frontend
pnpm install
pnpm dev
```

기본 주소:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`

## 환경 변수

### Backend

주요 값:

- `JWT_SECRET`
- `DB_URL`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `REDIS_PASSWORD`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `APP_BASE_URL`
- `APP_DEFAULT_LOCALE`
- `CORS_ALLOWED_ORIGIN`
- `COOKIE_SECURE`
- `SWAGGER_ENABLED`

예제 파일:

- [backend/.env.example](backend/.env.example)
- [backend/src/main/resources/application-local.example.yml](backend/src/main/resources/application-local.example.yml)

### Frontend

주요 값:

- `NEXT_PUBLIC_API_URL`
- `NEXT_PUBLIC_BACKEND_URL`
- `NEXT_PUBLIC_OAUTH_PROVIDERS`

예제 파일:

- [frontend/.env.example](frontend/.env.example)

## 테스트

빠른 확인:

```bash
cd backend
./gradlew test
```

컨테이너가 필요한 통합 테스트:

```bash
cd backend
./gradlew integrationTest
```

프론트 검사:

```bash
cd frontend
pnpm lint
pnpm build
```

## 이 템플릿이 어필되는 이유

- 인증을 단순 CRUD가 아니라 보안 경계와 세션 전략까지 포함해 설계했습니다.
- OAuth, JWT, Redis, 메일 인증, CSRF 대응, 레이스 컨디션 대응처럼 실무에서 자주 부딪히는 주제를 한 프로젝트 안에 담았습니다.
- 테스트를 계층별로 나눠 빠른 피드백과 실제 인프라 검증을 동시에 챙겼습니다.
- 관측성까지 포함해 “동작하는 코드”를 넘어서 “운영 가능한 서비스 시작점”에 가깝게 만들었습니다.

## 문서

- 백엔드 관측 가이드: [backend/HELP.md](backend/HELP.md)
- 이력서용 정리: [이력서를 위한 정보.md](이력서를%20위한%20정보.md)
- 포트폴리오용 정리: [포트폴리오를 위한 정보.md](포트폴리오를%20위한%20정보.md)
