export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  phone?: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
  username: string;
  nickname?: string;
  role: string;
}
