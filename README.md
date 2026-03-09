# Auth Template

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-16-000000?style=flat-square&logo=nextdotjs&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

새 프로젝트를 시작할 때마다 반복되는 인증 작업을 줄이기 위해 만든 풀스택 인증 템플릿입니다.

JWT 기반 Stateless 인증의 흔한 취약점(XSS, CSRF, RT 탈취, Race Condition)을 방어하는 패턴을 직접 구현하면서, 각 결정의 이유와 트레이드오프를 이해하는 데 집중했습니다.

---

## 목차

- [기술 스택](#기술-스택)
- [아키텍처 개요](#아키텍처-개요)
- [보안 기능](#보안-기능)
- [설계 결정 노트](#설계-결정-노트)
- [테스트 전략](#테스트-전략)
- [API](#api)
- [로컬 개발 환경 설정](#로컬-개발-환경-설정)
- [환경 변수](#환경-변수)
- [프로젝트 구조](#프로젝트-구조)

---

## 기술 스택

### Backend (`backend/`)

| 분류 | 기술 |
|------|------|
| Language / Runtime | Kotlin 2.2.21 / JDK 21 |
| Framework | Spring Boot 4.0.3, Spring Security 7.x |
| Database | MySQL 8.0 + Flyway |
| Cache / Session | Redis 7 |
| JWT | jjwt 0.12.6 |
| API Docs | springdoc-openapi 3.0.1 (Swagger UI) |
| Testing | MockK 1.14.2, Testcontainers 2.x |
| Infrastructure | Docker Compose |

### Frontend (`frontend/`)

| 분류 | 기술 |
|------|------|
| Framework | Next.js 16 + React 19 |
| Language | TypeScript |
| i18n | next-intl 4.8.3 (ko / en) |
| State | Zustand v5 |
| HTTP | Axios (401 자동 재발급 인터셉터) |
| UI | shadcn/ui + Tailwind CSS |
| Architecture | FSD (Feature-Sliced Design) |

---

## 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend (Next.js)                      │
│                                                             │
│  Access Token  → Zustand (JS 메모리)   ← XSS 방어           │
│  Refresh Token → HttpOnly Cookie       ← JS 접근 불가        │
│                                                             │
│  401 발생 시 Axios interceptor가 /reissue 자동 호출 후 재시도 │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS
┌───────────────────────────▼─────────────────────────────────┐
│                   Backend (Spring Boot)                     │
│                                                             │
│  Spring Security Filter                                     │
│    └─ JWT 검증 → sid 추출 → Redis RT:{sid} 존재 확인         │
│                                                             │
│  Redis                                                      │
│    ├─ RT:{sid}           세션 화이트리스트 (HMAC-SHA256 해시) │
│    ├─ LOCK:REISSUE:{sid} 동시 재발급 Race Condition 방어      │
│    └─ RATE:{type}:{key}  Rate Limiting (Lua script 원자성)   │
│                                                             │
│  MySQL (Flyway 마이그레이션)                                  │
│    └─ members                                               │
└─────────────────────────────────────────────────────────────┘
```

**핵심 설계 원칙**

- **Stateless JWT + 서버 측 세션 화이트리스트**: 순수 Stateless JWT는 로그아웃 후에도 토큰이 유효하다는 문제가 있습니다. Redis에 `RT:{sid}` 키를 두어 로그아웃 시 즉시 무효화하는 방식으로 이 문제를 해결했습니다. AT는 30분 수명으로 Redis 조회 없이 검증하고, 세션 유효성만 Redis로 확인합니다.
- **트랜잭션 커밋 후 이메일 발송**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 조합으로 DB 롤백 시 이메일이 발송되는 불일치를 방지합니다.
- **FSD(Feature-Sliced Design)**: 프론트엔드를 `app/`(라우팅), `features/`(도메인), `shared/`(공용)으로 계층화하여 도메인 간 의존성을 명확히 통제합니다.

---

## 보안 기능

### 1. JWT 토큰 전략

| 항목 | Access Token | Refresh Token |
|------|-------------|---------------|
| 저장 위치 | Zustand (JS 메모리) | HttpOnly Cookie |
| 쿠키 경로 | — | `path=/api/auth` |
| 유효 기간 | 30분 | Sliding 7일 / 절대 최대 30일 |

- AT를 메모리에만 보관하여 XSS로 인한 토큰 탈취를 차단합니다.
- RT 쿠키 `path=/api/auth` 설정으로 다른 API 요청에 쿠키가 첨부되지 않도록 범위를 제한합니다.

### 2. 세션 화이트리스트

RT 형식: `{sid}.{base64url(SecureRandom 32B)}`

Redis에는 RT 원문 대신 **HMAC-SHA256 해시**만 저장합니다. 재발급 시 RT를 항상 새 값으로 교체(회전)하며, 만약 교체된 구 RT로 재발급이 시도되면 해시 불일치로 탈취 시도로 간주하고 해당 세션을 즉시 무효화합니다.

### 3. Race Condition 방어

여러 탭에서 동시에 토큰 만료가 발생하면 `/reissue` 요청이 중복으로 들어옵니다. Redis `SETNX LOCK:REISSUE:{sid}` (TTL 3초) 분산 락으로 첫 번째 요청만 처리하고, 나머지는 `409 Conflict`로 응답합니다. 클라이언트는 409를 받으면 잠시 후 재시도하여 새 AT를 얻습니다.

### 4. Rate Limiting

Redis Lua Script로 카운터 증가를 원자적으로 처리하여 동시 요청에서도 정확한 제한을 보장합니다.

| 대상 | 한도 | 기준 |
|------|------|------|
| 로그인 | 20회 / 1시간 | IP |
| 로그인 | 10회 / 15분 | 이메일 |
| 회원가입 | 5회 / 1시간 | IP |

한도 초과 시 `429 Too Many Requests` + `Retry-After` 헤더를 반환합니다.

### 5. CSRF 이중 방어

- RT 쿠키 `SameSite=Strict`: 타 사이트에서의 요청에 쿠키가 첨부되지 않습니다.
- `/reissue`, `/logout`에 `X-CSRF-GUARD` 커스텀 헤더 검증: 브라우저는 크로스 오리진 요청에 임의 헤더를 추가할 수 없으므로, 이 헤더가 없는 요청은 거부합니다.

### 6. NIST SP 800-63B 비밀번호 정책

단순 복잡도 규칙 대신 NIST 권고안을 따릅니다.

- **유출 비밀번호 차단**: [HaveIBeenPwned API](https://haveibeenpwned.com/API/v3#PwnedPasswords)를 k-익명성 모델(SHA-1 prefix 5자리)로 조회합니다. 전체 해시를 외부로 전송하지 않아 프라이버시를 보호합니다.
- **컨텍스트 단어 차단**: 서비스명, 이메일 로컬파트 등 예측 가능한 단어를 비밀번호에 포함할 수 없습니다.
- **비밀번호 변경/재설정 시 전체 세션 무효화**: 탈취된 세션을 즉시 강제 종료합니다.
- **이메일 인증 필수**: 미인증 계정은 로그인이 불가합니다.

### 7. 로그 보안

- **MDC 요청 추적**: 모든 요청에 `requestId`와 `clientIp`를 MDC에 삽입합니다. 분산 환경에서도 하나의 요청 흐름을 로그로 추적할 수 있습니다.
- **Log Forging 방어**: 외부에서 유입된 값(헤더, 파라미터)의 개행 문자를 치환하여 로그 위조 공격을 방어합니다.
- **이메일 마스킹**: 로그에 이메일이 기록될 때 `t***@example.com` 형식으로 자동 마스킹합니다.

### 8. OAuth2 소셜 로그인

Google, Naver, Kakao 소셜 로그인을 지원합니다.

AT를 리다이렉트 URL에 포함하면 브라우저 히스토리에 남는 위험이 있으므로, **일회성 코드(UUID) 방식**을 사용합니다. 백엔드가 AT를 Redis에 60초 TTL로 저장하고, 프론트엔드가 코드를 AT로 교환한 뒤 코드는 즉시 삭제됩니다.

---

## 설계 결정 노트

기능 구현 과정에서 선택의 이유가 될 만한 결정들을 정리했습니다.

### Flyway Baseline 전략

처음부터 Flyway를 도입하지 않고 수동으로 테이블을 생성한 뒤 나중에 마이그레이션을 추가하는 상황은 실무에서 자주 마주칩니다. 이 프로젝트에서도 동일한 상황이 발생했습니다.

이미 존재하는 테이블에 Flyway를 적용하면 V1, V2 스크립트가 재실행되어 `Table already exists` 오류가 발생합니다. `baseline-on-migrate: true` + `baseline-version: 2` 설정으로 기존 스키마를 V2 시점으로 기록하고, V3부터만 자동 적용되도록 처리했습니다. 이후 V3, V4 스크립트는 Flyway가 정상적으로 관리합니다.

### OAuth 계정과 로컬 계정의 UX 분리

소셜 로그인으로 가입한 계정은 비밀번호가 없습니다. 마이페이지에서 비밀번호 변경 폼을 그대로 노출하면 의미 없는 UI이거나 오류를 유발합니다.

`GET /api/members/me` 응답에 `provider` 필드를 포함시켜 프론트엔드가 계정 유형을 판별하도록 했습니다. 소셜 계정이면 비밀번호 변경 섹션 자체를 렌더링하지 않고, 같은 이메일로 다른 소셜 제공자로 가입을 시도하면 명시적인 오류 메시지로 안내합니다.

### 비밀번호 없는 계정의 DB 설계

OAuth 계정은 비밀번호가 없으므로 `password` 컬럼을 `NULL` 허용으로 변경했습니다. `provider_key` 컬럼은 `GENERATED ALWAYS AS (IF(provider IS NOT NULL, CONCAT(provider, ':', provider_id), NULL))` 생성 컬럼으로, MySQL UNIQUE 인덱스가 NULL을 중복으로 보지 않는 특성을 활용해 로컬 계정은 여러 개 허용하면서 소셜 계정의 (provider, provider_id) 조합은 유일하게 강제합니다.

### 이메일 열거 공격(Enumeration Attack) 방어

비밀번호 재설정 요청 시, 해당 이메일이 존재하지 않더라도 항상 "메일을 발송했습니다"로 응답합니다. 이메일 존재 여부를 외부에 노출하지 않습니다.

---

## 테스트 전략

단위 테스트 → 슬라이스 테스트 → 통합 테스트 3계층으로 구성했습니다.

### 단위 테스트 (MockK)

Spring Context 없이 순수 로직을 검증합니다.

| 대상 | 주요 검증 항목 |
|------|--------------|
| `AuthService` | 로그인 성공/실패, 재발급 Race Condition, RT 해시 불일치 시 세션 즉시 무효화 |
| `MemberService` | 이메일 중복 가입, 이메일 소문자 정규화, 존재하지 않는 회원 조회 |
| `EmailVerificationService` | 인증 토큰 저장 및 이벤트 발행, 이미 인증된 이메일 재인증 방지 |
| `PasswordResetService` | 미가입 이메일 요청 시 200 반환 (열거 공격 방지), 재설정 후 전체 세션 무효화 |
| `JwtProvider` | AT 생성/검증, 변조 토큰, 만료 토큰 |

### 슬라이스 테스트 (`@WebMvcTest`)

실제 `SecurityConfig`를 임포트하여 Spring Security 필터 체인이 완전히 활성화된 상태에서 HTTP 레이어를 검증합니다. JWT 인증 흐름, CSRF 헤더 검증, 쿠키 설정, `ProblemDetail` 응답 구조를 실제 HTTP 요청/응답으로 확인합니다.

| 대상 | 주요 검증 항목 |
|------|--------------|
| `AuthController` | RT HttpOnly 쿠키 발급, X-CSRF-GUARD 누락 시 400, Rate Limit 시 Retry-After 헤더 |
| `MemberController` | 인증 필터 동작(미인증 401), 탈퇴 시 RT 쿠키 maxAge=0, Bean Validation 서비스 전 차단 |

### 통합 테스트 (Testcontainers + 실제 Redis)

Redis 컨테이너를 띄워 실제 명령어 동작을 검증합니다. 단위 테스트로는 확인할 수 없는 TTL 설정, SETNX 원자성, Lua 스크립트 동작을 직접 확인합니다.

| 대상 | 주요 검증 항목 |
|------|--------------|
| `RefreshTokenStore` | TTL 실제 설정 확인, SETNX 락 중복 방지, 멀티 세션 Lua 원자 삭제 |
| `RateLimiter` | 카운트 누적 정확성, TTL 자동 설정(INCR 후 EXPIRE 누락 방지), 만료 후 카운트 초기화 |
| `contextLoads` | 전체 스프링 컨텍스트 로딩 검증 (MySQL + Redis Testcontainers) |

---

## API

### 인증 (`/api/auth`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/auth/login` | 로그인 (AT 반환 + RT 쿠키 설정) |
| POST | `/api/auth/reissue` | 토큰 재발급 (RT 쿠키 사용) |
| POST | `/api/auth/logout` | 로그아웃 (세션 무효화 + 쿠키 만료) |
| GET | `/api/auth/verify-email?token=` | 이메일 인증 |
| POST | `/api/auth/password-reset/request` | 비밀번호 재설정 이메일 발송 |
| POST | `/api/auth/password-reset/confirm` | 비밀번호 재설정 (토큰 + 새 비밀번호) |
| GET | `/api/auth/oauth2/token?code=` | OAuth 일회성 코드 → AT 교환 |

### 회원 (`/api/members`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/members` | 회원가입 |
| GET | `/api/members/me` | 내 정보 조회 |
| PATCH | `/api/members/me/profile` | 닉네임 수정 |
| PATCH | `/api/members/me/password` | 비밀번호 변경 |
| DELETE | `/api/members/me` | 회원 탈퇴 (세션 정리 + 쿠키 만료) |

### 프론트엔드 페이지

| 경로 | 설명 |
|------|------|
| `/login` | 로그인 (소셜 로그인 버튼 포함) |
| `/signup` | 회원가입 |
| `/forgot-password` | 비밀번호 재설정 요청 |
| `/verify-email` | 이메일 인증 처리 |
| `/reset-password` | 비밀번호 재설정 |
| `/dashboard` | 마이페이지 (프로필 수정, 비밀번호 변경, 탈퇴) |
| `/auth/callback` | OAuth 소셜 로그인 콜백 처리 |

---

## 로컬 개발 환경 설정

### 사전 요구사항

- Docker & Docker Compose
- JDK 21
- Node.js 20+, pnpm

### 1. 인프라 실행

Docker Compose로 MySQL 8과 Redis 7을 한 번에 실행합니다.

```bash
cd auth-template/backend

# 환경 변수 파일 생성
cp .env.example .env

# MySQL + Redis 실행
docker compose up -d
```

### 2. 백엔드 실행

```bash
# application-local.yml에 이메일(Gmail 앱 비밀번호), OAuth 키 등 입력
# 이후 실행
./gradlew bootRun
```

로컬 `application-local.yml`이 `.env`가 아닌 YAML 파일로 민감 정보를 관리하는 이유: `./gradlew bootRun`은 Docker Compose와 달리 `.env` 파일을 자동으로 읽지 않습니다.

> Swagger UI: `http://localhost:8080/swagger-ui/index.html`
> (로컬에서는 `SWAGGER_ENABLED=true`로 활성화 필요)

### 3. 프론트엔드 실행

```bash
cd auth-template/frontend
pnpm install
pnpm dev
```

> `http://localhost:3000`

> **주의**: 로컬 HTTP 환경에서 RT 쿠키가 전송되려면 백엔드 `application-local.yml`에 `cookie.secure: false`가 설정되어 있어야 합니다.

---

## 환경 변수

### Backend (`.env` + `application-local.yml`)

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/authtemplate?...` |
| `MYSQL_USER` | DB 사용자명 | `authuser` |
| `MYSQL_PASSWORD` | DB 비밀번호 | — |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `REDIS_PASSWORD` | Redis 비밀번호 | — |
| `JWT_SECRET` | JWT 서명 키 (256비트 이상, 운영 필수 교체) | — |
| `MAIL_USERNAME` | 발신자 이메일 | `your@gmail.com` |
| `MAIL_PASSWORD` | Gmail 앱 비밀번호 | `xxxx xxxx xxxx xxxx` |
| `CORS_ALLOWED_ORIGIN` | 허용 Origin | `http://localhost:3000` |
| `COOKIE_SECURE` | HTTPS 전용 쿠키 (운영: `true`) | `false` |
| `APP_BASE_URL` | 이메일 링크 Base URL | `http://localhost:3000` |
| `SWAGGER_ENABLED` | Swagger UI 활성화 | `false` |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID | — |
| `NAVER_CLIENT_ID` | Naver OAuth 클라이언트 ID | — |
| `KAKAO_CLIENT_ID` | Kakao OAuth 클라이언트 ID | — |

---

## 프로젝트 구조

```
auth-template/
├── infra/
│   └── nginx/
│       ├── nginx.conf              # worker, upstream, HTTP→HTTPS 리다이렉트
│       └── conf.d/
│           └── auth-template.conf  # SSL, 보안 헤더, X-Forwarded-For 스푸핑 방지, 라우팅
│
├── backend/
│   ├── src/main/kotlin/.../authtemplate/
│   │   ├── auth/               # JWT, 세션, RT, OAuth2, 비밀번호 재설정
│   │   ├── member/             # 회원가입, 정보 수정, 이메일 인증, 탈퇴
│   │   └── common/             # Security 설정, 예외, 응답, MDC, Rate Limiting
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway V1~V4 SQL
│   │   └── application.yml
│   ├── docker-compose.yml
│   └── .env.example
│
└── frontend/
    ├── src/
    │   ├── app/[locale]/
    │   │   ├── (auth)/         # login, signup, forgot-password, verify-email, reset-password
    │   │   ├── (main)/         # dashboard (인증 가드 레이아웃)
    │   │   └── auth/callback/  # OAuth 콜백
    │   ├── features/
    │   │   ├── auth/           # 로그인, 로그아웃, 토큰 관리, 소셜 로그인
    │   │   └── member/         # 프로필, 비밀번호 변경, 탈퇴
    │   └── shared/
    │       ├── api/            # Axios 인스턴스 + 401 재발급 인터셉터
    │       └── components/ui/  # shadcn/ui 컴포넌트
    └── messages/
        ├── ko.json
        └── en.json
```

---

## 사용 안내

Fork 후 새 프로젝트의 시작점으로 자유롭게 사용하세요.
보안 이슈나 개선 제안은 Issue로 남겨주시면 반영하겠습니다.

---

<p align="center">MIT License</p>
