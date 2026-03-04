import { create } from "zustand";
import { MemberResponse } from "../types/auth";

interface AuthState {
  accessToken: string | null;
  member: MemberResponse | null;
  setAccessToken: (token: string) => void;
  setMember: (member: MemberResponse) => void;
  clearAuth: () => void;
}

// AT는 메모리에만 보관 — XSS로부터 보호, 새로고침 시 reissue로 복구
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  setAccessToken: (token) => set({ accessToken: token }),
  setMember: (member) => set({ member }),
  clearAuth: () => set({ accessToken: null, member: null }),
}));
