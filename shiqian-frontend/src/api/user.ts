import { http } from './http';
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UpdateUserRequest
} from '../types/user';

export const userApi = {
  login(params: LoginRequest) {
    return http.post<unknown, LoginResponse>('/user/login', params);
  },
  register(params: RegisterRequest) {
    return http.post<unknown, void>('/user/register', params);
  },
  updateCurrentUser(params: UpdateUserRequest) {
    return http.put<unknown, void>('/user/me', params);
  }
};
