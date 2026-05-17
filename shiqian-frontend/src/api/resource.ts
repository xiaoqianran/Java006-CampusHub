import { http } from './http';
import type {
  Category,
  PageResult,
  ResourceCreateRequest,
  ResourceItem,
  ResourceQuery
} from '../types/resource';

export const resourceApi = {
  pageResources(params: ResourceQuery) {
    return http.get<unknown, PageResult<ResourceItem>>('/resource', { params });
  },
  getResource(id: number) {
    return http.get<unknown, ResourceItem>(`/resource/${id}`);
  },
  createResource(params: ResourceCreateRequest) {
    return http.post<unknown, void>('/resource', params);
  },
  listCategoryTree() {
    return http.get<unknown, Category[]>('/category/tree');
  }
};
