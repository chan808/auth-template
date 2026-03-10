# Frontend

이 프론트엔드는 인증/회원 기능을 실제 사용자 흐름으로 검증하기 위한 Next.js 애플리케이션입니다.  
백엔드 API를 단순히 호출하는 데서 끝나지 않고, 로그인 상태 관리, 토큰 재발급, OAuth 콜백, i18n 라우팅까지 포함합니다.

## 기술 스택

- Next.js 16
- React 19
- TypeScript
- next-intl
- TanStack Query
- Zustand
- Axios
- Tailwind CSS 4
- shadcn/ui

## 주요 역할

- 로그인, 회원가입, 이메일 인증, 비밀번호 재설정 화면 제공
- OAuth 로그인 버튼과 콜백 처리
- 로그인 후 대시보드에서 프로필 수정, 비밀번호 변경, 탈퇴 처리
- Access Token 메모리 저장
- 401 발생 시 Refresh Token 기반 자동 재발급 시도
- ko/en 국제화 지원

## 실행

### 환경 변수

`.env.example`을 `.env.local`로 복사해서 사용합니다.

- `NEXT_PUBLIC_API_URL`: 백엔드 API 주소
- `NEXT_PUBLIC_BACKEND_URL`: OAuth 진입에 사용할 백엔드 주소
- `NEXT_PUBLIC_OAUTH_PROVIDERS`: 화면에 노출할 소셜 로그인 제공자 목록

기본 예시:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_OAUTH_PROVIDERS=google
```

### 개발 서버

```bash
pnpm install
pnpm dev
```

### 검증

```bash
pnpm lint
pnpm build
```

## 구조

```text
src/
├─ app/
│  ├─ [locale]/
│  │  ├─ (auth)/
│  │  ├─ (main)/
│  │  └─ auth/callback/
├─ features/
│  ├─ auth/
│  └─ member/
├─ shared/
│  ├─ api/
│  └─ components/
└─ i18n/
```

## 구현 포인트

- 로그인 상태는 Zustand로 관리하고, Access Token은 메모리에만 둡니다.
- Refresh Token은 백엔드가 발급하는 HttpOnly 쿠키를 사용합니다.
- Axios interceptor에서 재발급 흐름을 처리합니다.
- 이메일 인증과 비밀번호 재설정 링크는 locale 없는 경로로 들어와도 기본 locale 경로로 리다이렉트됩니다.

이 프로젝트의 전체 맥락은 루트 [README.md](../README.md)에서 확인할 수 있습니다.
