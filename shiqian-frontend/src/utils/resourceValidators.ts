import type { ResourceCreateRequest } from '../types/resource';

export function validateResourceCreate(form: Partial<ResourceCreateRequest>): string {
  if (!form.title?.trim()) {
    return '资源标题不能为空';
  }
  if (form.title.length > 200) {
    return '资源标题最多200个字符';
  }
  if (form.description && form.description.length > 1000) {
    return '资源描述最多1000个字符';
  }
  if (!form.categoryId || form.categoryId < 1) {
    return '分类ID必须大于0';
  }
  if (!form.fileUrl?.trim()) {
    return '文件地址不能为空';
  }
  if (form.fileUrl.length > 500) {
    return '文件地址最多500个字符';
  }
  if (form.fileSize === undefined || form.fileSize === null) {
    return '文件大小不能为空';
  }
  if (form.fileSize < 0) {
    return '文件大小不能为负数';
  }
  if (!form.fileType?.trim()) {
    return '文件类型不能为空';
  }
  if (form.fileType.length > 100) {
    return '文件类型最多100个字符';
  }
  return '';
}
