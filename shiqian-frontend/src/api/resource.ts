import { http } from './http';
import type {
  Category,
  PageResult,
  ResourceCreateRequest,
  ResourceItem,
  ResourceQuery,
  ResourceUpdateRequest
} from '../types/resource';

export interface FavoriteStatus { data?: boolean }

export const resourceApi = {
  // 列表 & 搜索
  pageResources(params: ResourceQuery) {
    return http.get<unknown, PageResult<ResourceItem>>('/resource', { params });
  },
  searchResources(keyword: string, page = 1, size = 10) {
    return http.get<unknown, any>('/resource/search', { params: { keyword, page, size } });
  },

  // 详情
  getResource(id: number) {
    return http.get<unknown, ResourceItem>(`/resource/${id}`);
  },

  // 写操作
  createResource(params: ResourceCreateRequest) {
    return http.post<unknown, void>('/resource', params);
  },
  updateResource(id: number, params: ResourceUpdateRequest) {
    return http.put<unknown, void>(`/resource/${id}`, params);
  },
  deleteResource(id: number) {
    return http.delete<unknown, void>(`/resource/${id}`);
  },

  // 下载（MQ 异步）
  downloadResource(id: number) {
    return http.post<unknown, void>(`/resource/${id}/download`);
  },

  // 收藏体系
  addFavorite(id: number) {
    return http.post<unknown, void>(`/resource/${id}/favorite`);
  },
  removeFavorite(id: number) {
    return http.delete<unknown, void>(`/resource/${id}/favorite`);
  },
  isFavorited(id: number) {
    return http.get<unknown, boolean>(`/resource/${id}/favorite`);
  },

  // 分类
  listCategoryTree() {
    return http.get<unknown, Category[]>('/category/tree');
  },

  // 管理员审核
  auditResource(id: number, status: 1 | 2) {
    return http.put<unknown, void>(`/resource/${id}/audit`, null, { params: { status } });
  }
};
