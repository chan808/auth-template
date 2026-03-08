import api from "@/shared/api/axios";
import { ApiResponse } from "@/shared/types/api";
import { LoginRequest, TokenResponse } from "../types/auth";

export const authApi = {
  login: (data: LoginRequest) =>
    api.post<ApiResponse<TokenResponse>>("/api/auth/login", data),

  // X-CSRF-GUARD: 백엔드 CSRF 이중 방어 요구사항
  reissue: () =>
    api.post<ApiResponse<TokenResponse>>("/api/auth/reissue", null, {
      headers: { "X-CSRF-GUARD": "1" },
    }),

  logout: () =>
    api.post<ApiResponse<void>>("/api/auth/logout", null, {
      headers: { "X-CSRF-GUARD": "1" },
    }),

  verifyEmail: (token: string) =>
    api.get<ApiResponse<void>>(`/api/auth/verify-email?token=${token}`),

  /** OAuth 로그인 후 one-time code를 AT로 교환 */
  exchangeOAuthCode: (code: string) =>
    api.get<ApiResponse<{ accessToken: string }>>(`/api/auth/oauth2/token?code=${code}`),

  requestPasswordReset: (email: string) =>
    api.post<ApiResponse<void>>("/api/auth/password-reset/request", { email }),

  confirmPasswordReset: (token: string, newPassword: string) =>
    api.post<ApiResponse<void>>("/api/auth/password-reset/confirm", {
      token,
      newPassword,
    }),
};
