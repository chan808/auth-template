"use client";

import { useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "next-intl";
import { authApi } from "@/features/auth/api/authApi";
import { useAuthStore } from "@/features/auth/stores/authStore";

/**
 * OAuth2 로그인 후 백엔드가 리다이렉트하는 페이지
 * ?code= 쿼리로 one-time code를 받아 AT로 교환 후 대시보드로 이동
 * locale 불필요 — /auth/callback (locale 없이 직접 접근)
 */
export default function OAuthCallbackPage() {
  const router = useRouter();
  const locale = useLocale();
  const searchParams = useSearchParams();
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  // React Strict Mode 개발 환경에서 useEffect가 두 번 실행됨
  // one-time code는 첫 번째 호출에서 Redis에서 삭제되므로 두 번째 호출 시 OAUTH_CODE_NOT_FOUND
  const hasExchanged = useRef(false);

  useEffect(() => {
    if (hasExchanged.current) return;
    hasExchanged.current = true;

    const code = searchParams.get("code");
    const error = searchParams.get("error");

    if (error) {
      router.replace(`/${locale}/login?error=${encodeURIComponent(error)}`);
      return;
    }

    if (!code) {
      router.replace(`/${locale}/login`);
      return;
    }

    authApi
      .exchangeOAuthCode(code)
      .then((res) => {
        const at = res.data.data?.accessToken;
        if (at) setAccessToken(at);
        router.replace(`/${locale}/dashboard`);
      })
      .catch(() => {
        router.replace(`/${locale}/login?error=${encodeURIComponent("소셜 로그인에 실패했습니다.")}`);
      });
  }, []);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-muted-foreground">로그인 처리 중...</p>
    </main>
  );
}
