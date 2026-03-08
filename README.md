# Auth Template

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-16-000000?style=flat-square&logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?style=flat-square&logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

> 새 프로젝트의 시작점으로 사용할 수 있는 **인증 템플릿**입니다.
> 프로덕션 수준의 보안 설계를 실제로 구현해보며 학습한 내용을 담았습니다.

JWT 기반 Stateless 인증의 흔한 취약점(XSS, CSRF, RT 탈취, Race Condition 등)을 방어하는 패턴을 Spring Boot + Next.js 풀스택으로 구현했습니다. 보안 기능을 처음부터 직접 구현함으로써 각 설계 결정의 트레이드오프를 이해하는 것에 초점을 맞췄습니다.

---

## 목차

- [기술 스택](#기술-스택)
- [아키텍처 개요](#아키텍처-개요)
- [보안 기능](#보안-기능)
- [API 엔드포인트 및 페이지](#api-엔드포인트-및-페이지)
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
| Build | Gradle 9.3.1 |
| Database | MySQL 8.0 + Flyway (마이그레이션) |
| Cache / Session | Redis 7 |
| JWT | jjwt 0.12.6 |
| API Docs | springdoc-openapi 3.0.1 (Swagger UI) |
| Testing | MockK 1.14.2, springmockk, Testcontainers 2.x |
| Infrastructure | Docker Compose |

### Frontend (`frontend/`)

| 분류 | 기술 |
|------|------|
| Framework | Next.js 16 + React 19 |
| Language | TypeScript |
| Package Manager | pnpm |
| i18n | next-intl 4.8.3 (ko / en) |
| State Management | Zustand v5 |
| HTTP Client | Axios (401 interceptor) |
| UI | shadcn/ui + Tailwind CSS |
| Compiler | React Compiler (experimental) |
| Architecture | FSD (Feature-Sliced Design) |

---

## 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Next.js)                   │
│                                                             │
│  Access Token ──► Zustand (in-memory)  ← XSS 방어          │
│  Refresh Token ──► HttpOnly Cookie     ← JS 접근 불가       │
│                                                             │
│  401 발생 시 Axios interceptor가 자동으로 /reissue 호출     │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTPS
┌────────────────────────────▼────────────────────────────────┐
│                    Backend (Spring Boot)                    │
│                                                             │
│  Spring Security Filter Chain                               │
│    └─ JWT 검증 → sid 추출 → Redis RT:{sid} 존재 확인        │
│                                                             │
│  Redis                                                      │
│    ├─ RT:{sid}        → RT 해시 + 만료 (세션 화이트리스트)  │
│    ├─ LOCK:REISSUE:{sid} → Reissue Race Condition 방어      │
│    └─ RATE:{type}:{key}  → Rate Limiting (Lua script)       │
│                                                             │
│  MySQL                                                      │
│    └─ members, email_verifications, password_resets         │
└─────────────────────────────────────────────────────────────┘
```

**핵심 설계 원칙**

- **Stateless JWT + Redis 세션 화이트리스트**: Stateless JWT의 성능 이점과 서버 측 세션 무효화 능력을 모두 확보합니다. AT는 짧은 수명(30분)으로 Redis 조회 없이 검증하고, 세션 유효성은 Redis의 `RT:{sid}` 키 존재 여부로 확인합니다.
- **비동기 이메일 발송**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`를 조합하여 트랜잭션 커밋 이후에만 이메일을 발송합니다. DB 롤백 시 이메일이 발송되는 불일치를 방지합니다.
- **FSD (Feature-Sliced Design)**: 프론트엔드를 `app/`(라우팅), `features/`(도메인 로직), `shared/`(공용 컴포넌트)로 계층 분리하여 관심사를 명확히 구분합니다.

---

## 보안 기능

인증 시스템의 가장 중요한 부분입니다. 각 기능이 **어떤 위협**을 방어하는지에 집중했습니다.

### 1. JWT 토큰 전략

| 항목 | Access Token | Refresh Token |
|------|-------------|---------------|
| 저장 위치 | Zustand (JS 메모리) | HttpOnly Cookie (`path=/api/auth`) |
| 유효 기간 | 30분 | Sliding 7일 / Absolute max 30일 |
| 목적 | API 인증 | AT 재발급 |

- AT를 메모리에 보관하여 **XSS로 인한 토큰 탈취**를 차단합니다 (localStorage/sessionStorage 사용 안 함).
- RT를 `HttpOnly` 쿠키에 보관하여 **JS에서 직접 접근 불가**하게 합니다.
- RT 쿠키 `path=/api/auth`로 **불필요한 쿠키 전송 범위**를 최소화합니다.

### 2. 세션 화이트리스트 (sid 기반)

RT 형식: `{sid}.{base64url(SecureRandom 32B)}`

Redis에는 RT 원문 대신 **HMAC-SHA256 해시**만 저장합니다. 모든 인증 요청에서 Redis의 `RT:{sid}` 키를 확인하여 세션이 유효한지 검증합니다. 로그아웃 시 해당 키를 삭제하면 **즉시 세션 무효화**가 가능합니다 (JWT의 고질적인 문제 해결).

### 3. RT 회전 및 탈취 감지

Reissue 시 RT를 항상 새 토큰으로 교체(회전)합니다. 교체된 구 RT로 재발급을 시도하면 Redis에 저장된 해시와 불일치하므로 **탈취 시도로 감지**하고 해당 세션을 즉시 강제 종료합니다.

### 4. Race Condition 방어 (Concurrent Reissue)

네트워크 지연 등으로 `/reissue` 요청이 동시에 도달할 경우, Redis `SETNX LOCK:REISSUE:{sid}` (TTL 3초)를 이용한 **분산 락**으로 첫 번째 요청만 처리하고 나머지는 `409 Conflict`로 응답합니다.

### 5. Rate Limiting

Redis Lua Script를 사용해 **원자적으로** 카운터를 증가시켜 정확한 Rate Limiting을 구현합니다. 한도 초과 시 `429 Too Many Requests`와 `Retry-After` 헤더를 반환합니다.

| 대상 | 한도 | 기준 |
|------|------|------|
| 로그인 | 20회 / 1시간 | IP |
| 로그인 | 10회 / 15분 | 이메일 |
| 회원가입 | 5회 / 1시간 | IP |

### 6. CSRF 방어

- RT 쿠키 `SameSite=Strict`: 타 사이트에서 발생한 요청에는 쿠키가 첨부되지 않습니다.
- `/reissue`, `/logout` 엔드포인트에 **`X-CSRF-GUARD` 커스텀 헤더 필수 검증**: 브라우저는 크로스 오리진 요청에 임의 헤더를 추가할 수 없으므로 (CORS Preflight 차단), 이 헤더가 없는 요청은 거부합니다.

### 7. NIST SP 800-63B 준수 비밀번호 정책

단순한 복잡도 규칙을 넘어, NIST 가이드라인에 따른 실질적 보안을 구현합니다.

- **이메일 인증 필수**: 미인증 계정은 로그인 불가
- **침해된 비밀번호 차단**: [HaveIBeenPwned API](https://haveibeenpwned.com/API/v3#PwnedPasswords)를 k-익명성 모델 (SHA-1 prefix 5자리 전송)로 조회하여 **유출된 비밀번호 사용을 차단**합니다. 전체 해시를 외부로 전송하지 않아 프라이버시를 보호합니다.
- **컨텍스트 단어 차단**: 서비스명, 이메일 로컬파트 등 추측하기 쉬운 단어를 비밀번호에 사용할 수 없습니다.
- **길이 기반 정책**: 8~64자 / 출력 가능한 ASCII / 영문자 + 숫자 + 특수문자 조합 필수
- **비밀번호 재설정**: 30분 TTL 토큰 / 변경 시 **모든 세션 일괄 무효화**
- **비밀번호 변경**: 기존 비밀번호 확인 후 전체 세션 무효화

### 8. 보안 헤더 및 로그 보안

- **HSTS**: 프로덕션 환경에서만 활성화
- **Referrer-Policy**: `no-referrer`
- **MDC 추적**: 모든 요청에 `requestId` / `clientIp`를 MDC에 삽입하여 로그 추적 용이
- **Log Forging 방어**: 제어 문자(개행 등) 치환으로 로그 위조 공격 방어
- **이메일 마스킹**: 로그에 이메일 기록 시 자동 마스킹 (`t***@example.com`)

### 9. Swagger UI 보안

프로덕션 환경에서 `SWAGGER_ENABLED=false`(기본값)로 API 문서를 비활성화합니다.

---

## API 엔드포인트 및 페이지

### Backend API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/auth/login` | 로그인 (AT + RT 발급) |
| POST | `/api/auth/reissue` | 토큰 재발급 (RT 쿠키 사용) |
| POST | `/api/auth/logout` | 로그아웃 (세션 무효화) |
| GET | `/api/auth/verify-email` | 이메일 인증 (`?token=`) |
| POST | `/api/auth/password-reset/request` | 비밀번호 재설정 이메일 발송 |
| POST | `/api/auth/password-reset/confirm` | 비밀번호 재설정 확인 |
| POST | `/api/members` | 회원가입 |
| GET | `/api/members/me` | 내 정보 조회 |
| PATCH | `/api/members/me/password` | 비밀번호 변경 |

### Frontend Pages

| 경로 | 설명 |
|------|------|
| `/login` | 로그인 |
| `/signup` | 회원가입 |
| `/forgot-password` | 비밀번호 재설정 요청 |
| `/verify-email` | 이메일 인증 처리 |
| `/reset-password` | 비밀번호 재설정 |
| `/dashboard` | 프로필 조회 + 비밀번호 변경 |

---

## 로컬 개발 환경 설정

### 사전 요구사항

- Docker & Docker Compose
- JDK 21
- Node.js 20+
- pnpm

### 백엔드 실행

```bash
# 1. 저장소 클론
git clone https://github.com/YOUR_USERNAME/auth-template.git
cd auth-template/backend

# 2. 환경 변수 파일 복사 및 설정
cp .env.example .env
# .env 파일을 열어 DB/Redis 비밀번호 등 설정

# 3. 인프라 실행 (MySQL 8, Redis 7)
docker compose up -d

# 4. application-local.yml 설정
# Gmail 앱 비밀번호 등 이메일 설정 입력

# 5. 애플리케이션 실행
./gradlew bootRun
```

> Swagger UI: http://localhost:8080/swagger-ui/index.html

### 프론트엔드 실행

```bash
cd auth-template/frontend

# 의존성 설치
pnpm install

# 개발 서버 실행
pnpm dev
```

> 프론트엔드: http://localhost:3000

> **주의**: 로컬 개발 시 HTTP 환경에서 RT 쿠키가 전송되려면 백엔드의 `cookie.secure=false`로 설정해야 합니다.

---

## 환경 변수

### Backend `.env`

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/authdb?...` |
| `MYSQL_USER` | DB 사용자명 | `authuser` |
| `MYSQL_PASSWORD` | DB 비밀번호 | `your_password` |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `REDIS_PORT` | Redis 포트 | `6379` |
| `REDIS_PASSWORD` | Redis 비밀번호 | `your_redis_pw` |
| `JWT_SECRET` | JWT 서명 키 (256비트 이상, 프로덕션 필수 변경) | `base64_encoded_secret` |
| `MAIL_HOST` | SMTP 호스트 | `smtp.gmail.com` |
| `MAIL_USERNAME` | 발신자 이메일 | `your@gmail.com` |
| `MAIL_PASSWORD` | Gmail 앱 비밀번호 | `xxxx xxxx xxxx xxxx` |
| `CORS_ALLOWED_ORIGIN` | 허용할 프론트엔드 Origin | `http://localhost:3000` |
| `COOKIE_SECURE` | HTTPS 전용 쿠키 (프로덕션: `true`) | `false` |
| `APP_BASE_URL` | 이메일 링크에 사용될 프론트엔드 URL | `http://localhost:3000` |
| `SWAGGER_ENABLED` | Swagger UI 활성화 여부 | `false` |

---

## 프로젝트 구조

```
auth-template/
├── backend/                        # Spring Boot (Kotlin)
│   ├── src/main/kotlin/
│   │   └── io/github/chan808/authtemplate/
│   │       ├── auth/               # 인증 도메인 (JWT, 세션, RT 관리)
│   │       ├── member/             # 회원 도메인
│   │       ├── security/           # Security 설정, Filter, Rate Limiting
│   │       ├── mail/               # 이메일 발송 (비동기)
│   │       └── common/             # 공통 응답, 예외, 유틸
│   ├── src/main/resources/
│   │   ├── db/migration/           # Flyway SQL 마이그레이션
│   │   ├── scripts/                # Redis Lua 스크립트
│   │   ├── application.yml
│   │   └── application-local.yml
│   ├── docker-compose.yml          # MySQL + Redis
│   └── .env.example
│
└── frontend/                       # Next.js (TypeScript)
    ├── src/
    │   ├── app/                    # Next.js App Router
    │   │   └── [locale]/           # next-intl 동적 세그먼트
    │   │       ├── (auth)/         # 인증 페이지 (login, signup...)
    │   │       └── (main)/         # 인증 필요 페이지 (dashboard...)
    │   ├── features/               # FSD: 도메인별 기능
    │   │   ├── auth/               # 로그인, 로그아웃, 토큰 관리
    │   │   └── member/             # 회원 정보, 비밀번호 변경
    │   └── shared/                 # FSD: 공용 레이어
    │       ├── api/                # Axios 인스턴스, 인터셉터
    │       ├── components/ui/      # shadcn/ui 컴포넌트
    │       └── store/              # Zustand store (AT 보관)
    └── messages/
        ├── ko.json
        └── en.json
```

---

## 기여 및 사용

이 템플릿은 **Fork하여 새 프로젝트의 시작점**으로 자유롭게 사용하시기 바랍니다.

보안 이슈나 개선 제안은 Issue 또는 Pull Request로 환영합니다.

---

<p align="center">
  Made with Spring Boot + Next.js · MIT License
</p>
