import { http } from './http';
import type { LoginRequest, LoginResponse, RegisterRequest } from '../types/user';

export const userApi = {
  login(params: LoginRequest) {
    return http.post<unknown, LoginResponse>('/user/login', params);
  },
  register(params: RegisterRequest) {
    return http.post<unknown, void>('/user/register', params);
  }
};
