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
};
