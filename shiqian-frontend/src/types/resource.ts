export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

export interface Category {
  id: number;
  parentId: number;
  name: string;
  sortOrder?: number;
  icon?: string;
  status?: number;
  children?: Category[];
}

export interface ResourceItem {
  id: number;
  userId: number;
  title: string;
  description?: string;
  categoryId?: number;
  fileUrl: string;
  fileSize: number;
  fileType: string;
  downloadCount: number;
  version: number;
  status: number;
  createTime?: string;
  updateTime?: string;
}

export interface ResourceQuery {
  page: number;
  size: number;
  keyword?: string;
  categoryId?: number;
}

export interface ResourceCreateRequest {
  title: string;
  description?: string;
  categoryId: number;
  fileUrl: string;
  fileSize: number;
  fileType: string;
}

export interface ResourceUpdateRequest extends ResourceCreateRequest {}
