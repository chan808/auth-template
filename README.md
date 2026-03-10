# Auth Template

백엔드 중심 서비스에서 반복되는 회원가입, 로그인, OAuth, 세션 관리, 이메일 인증, 비밀번호 재설정, 관측성 구성을 빠르게 시작하기 위한 인증 템플릿입니다.

이 프로젝트는 단순히 로그인 기능을 붙이는 수준이 아니라, 실제 서비스에서 자주 문제가 되는 세션 무효화, 이메일 인증 회복 동선, 재전송 제한, 관측성, 테스트 가능성까지 포함한 시작점을 목표로 합니다.

## Stack

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
- Micrometer
- Prometheus
- Testcontainers
- MockK
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
- Nginx
- Prometheus
- Grafana

## What This Template Solves

- 이메일 회원가입 + 이메일 인증 + 로그인
- Google, Naver, Kakao OAuth 로그인
- Access Token + Opaque Refresh Token 기반 인증
- Redis 기반 세션 인덱스 관리와 전체 세션 무효화
- 비밀번호 재설정 메일 발송과 재설정 완료
- 회원가입, 로그인, 인증 메일 재전송 rate limiting
- 미인증 계정 재가입 복구
- 미인증 계정 정리 배치
- Actuator, Prometheus, Grafana 기반 운영 관측성

## Core Design

### Authentication

- Access Token은 JWT로 발급합니다.
- Refresh Token은 JWT가 아니라 `sid.random` 형식의 랜덤 토큰을 사용합니다.
- Refresh Token 원문은 저장하지 않고 해시만 Redis에 저장합니다.
- Redis에 회원별 세션 인덱스를 유지해서 로그아웃, 비밀번호 변경, 회원 탈퇴 시 전체 세션을 즉시 무효화할 수 있습니다.

### Account Recovery

- 이메일 미인증 상태에서 다시 회원가입하면 중복 가입으로 막지 않고 비밀번호를 갱신한 뒤 인증 메일을 다시 발송합니다.
- 인증 메일 재전송 API를 제공하며, 이전 인증 링크는 새 링크 발급 시 무효화됩니다.
- 일정 기간 지난 미인증 로컬 계정은 스케줄러가 정리합니다.

### Security

- JWT 시크릿은 외부 환경 변수로만 주입합니다.
- OAuth 계정과 로컬 계정을 분리 처리합니다.
- `AFTER_COMMIT` 기반 이벤트 처리로 DB 커밋 이후에 메일 발송과 세션 무효화를 수행합니다.
- IP, 이메일 단위 rate limit으로 인증 관련 남용을 제어합니다.

### Observability

- `health`, `info`, `prometheus` 엔드포인트를 노출합니다.
- 인증 성공/실패, 재발급 실패, 회원가입, 비밀번호 변경, 세션 무효화 같은 도메인 이벤트를 메트릭으로 기록합니다.
- Prometheus와 Grafana를 바로 붙일 수 있는 로컬 구성을 제공합니다.

## Project Structure

```text
auth-template/
|-- backend/
|   |-- src/main/kotlin
|   |-- src/main/resources
|   |-- src/test/kotlin
|   |-- docker-compose.yml
|   |-- .env.example
|   `-- OBSERVABILITY.md
|-- frontend/
|   |-- src
|   |-- messages
|   |-- public
|   `-- .env.example
|-- infra/
|   `-- nginx/
|-- 이력서내용.md
|-- 포트폴리오내용.md
`-- README.md
```

## Quick Start

### 1. Local Config

- `backend/.env.example`를 `backend/.env`로 복사합니다.
- `frontend/.env.example`를 `frontend/.env.local`로 복사합니다.

### 2. Infra

```bash
cd backend
docker compose up -d mysql redis minio
```

관측성까지 같이 띄우려면:

```bash
cd backend
docker compose --profile observability up -d prometheus grafana
```

### 3. Backend

```bash
cd backend
./gradlew bootRun
```

### 4. Frontend

```bash
cd frontend
pnpm install
pnpm dev
```

## Test

```bash
cd backend
./gradlew test
./gradlew integrationTest
```

```bash
cd frontend
pnpm lint
pnpm build
```

## Documents

- Backend observability guide: [backend/OBSERVABILITY.md](backend/OBSERVABILITY.md)
- Backend note: local Prometheus scraping requires `SPRING_PROFILES_ACTIVE=observability`, and the current OAuth login flow still assumes a single-server deployment until session storage is externalized.
- Resume write-up: [이력서내용.md](이력서내용.md)
- Portfolio write-up: [포트폴리오내용.md](포트폴리오내용.md)
