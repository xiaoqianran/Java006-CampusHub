export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface LoginUser {
  userId: number;
  username: string;
  nickname?: string;
  role: string;
}

export interface AuthToken {
  accessToken: string;
  refreshToken: string;
}
