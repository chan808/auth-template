export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
}

export interface MemberResponse {
  id: number;
  email: string;
  role: string;
}
