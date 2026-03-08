import api from "@/shared/api/axios";
import { ApiResponse } from "@/shared/types/api";
import { MemberResponse, SignupRequest } from "@/features/auth/types/auth";

export const memberApi = {
  // 인증 불필요 — SecurityConfig에서 permitAll
  signup: (data: SignupRequest) =>
    api.post<ApiResponse<MemberResponse>>("/api/members", data),

  getMyInfo: () => api.get<ApiResponse<MemberResponse>>("/api/members/me"),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    api.patch<ApiResponse<void>>("/api/members/me/password", data),
};
