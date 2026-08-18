export type Role = 'student' | 'admin'
export type ResourceStatus = '已发布' | '待审核' | '待修改' | '已拒绝' | '已下架'
export type ContentScene = 'BLOG' | 'GALLERY' | 'SHARE'
export type ContentSceneFilter = 'ALL' | ContentScene

export const CONTENT_SCENES: Array<{
  value: ContentScene
  label: string
  description: string
}> = [
  { value: 'BLOG', label: '博客', description: '观点、教程、经验和长文' },
  { value: 'GALLERY', label: '图片', description: '作品、相册和视觉内容' },
  { value: 'SHARE', label: '资料', description: '文件、源码、课件和讨论' }
]

export function contentSceneLabel(scene?: string) {
  return CONTENT_SCENES.find(item => item.value === scene)?.label || '资料'
}

export interface ResourceApiItem {
  id: number
  userId: number
  title: string
  // 旧字段（兼容历史数据）
  description?: string
  // 新字段（第一阶段主推）
  summary?: string
  contentMarkdown?: string
  contentType?: string
  contentScene?: ContentScene
  tags?: string
  categoryId?: number
  categoryIds?: number[]
  categoryNames?: string[]
  tagIds?: number[]
  tagNames?: string[]
  version?: number
  searchHighlights?: Record<string, string[]>
  fileUrl?: string
  fileSize?: number
  fileType?: string
  downloadCount?: number
  viewCount?: number
  status: number
  reviewReason?: string
  reviewerId?: number
  reviewTime?: string
  offlineReason?: string
  publishedTime?: string
  createTime?: string
  updateTime?: string
  attachments?: ResourceAttachmentItem[]
  authorNickname?: string   // 后端富化提供
}

export interface UploadedFileItem {
  originalName: string
  fileUrl: string
  fileSize: number
  fileType: string
  mimeType?: string
  assetKind?: string
  usageType?: string
  sortOrder?: number
}

// 第二阶段：资源附件（用于详情展示）
export interface ResourceAttachmentItem {
  id?: number
  resourceId?: number
  fileName: string
  fileUrl: string
  fileSize: number
  fileType?: string
  mimeType?: string
  assetKind?: string
  usageType?: string
  sortOrder?: number
}

export interface CategoryApiItem {
  id: number
  parentId: number
  name: string
  sortOrder: number
  icon?: string
  status: number
  children?: CategoryApiItem[]
}

export interface TagApiItem {
  id: number
  name: string
  status: number
}

export interface LoginUser {
  userId: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
  email?: string
  phone?: string
  avatar?: string
  status?: number
}

export interface ResourceItem {
  id: number
  title: string
  cat: string
  categoryId?: number
  categoryIds: number[]
  categoryNames: string[]
  scene: ContentScene
  tags?: string
  tagIds: number[]
  tagNames: string[]
  version: number
  type: string
  author: string
  userId?: number
  views: number
  downloads: number
  favs: number
  status: ResourceStatus
  desc: string
  // 新字段（详情页渲染用）
  summary?: string
  contentMarkdown?: string
  contentType?: string
  fileUrl?: string
  fileSize?: number
  // 第二阶段：附件列表
  attachments?: ResourceAttachmentItem[]
  reviewReason?: string
  reviewerId?: number
  reviewTime?: string
  offlineReason?: string
  publishedTime?: string
  searchHighlights?: Record<string, string[]>
}

export interface ResourceVersionItem {
  id: number
  resourceId: number
  versionNumber: number
  title: string
  summary?: string
  description?: string
  markdownContent?: string
  categoryIds: number[]
  tagNames: string[]
  contentScene: ContentScene
  resourceType: string
  fileUrl?: string
  fileSize?: number
  fileType?: string
  attachments: ResourceAttachmentItem[]
  changeDescription?: string
  createdBy: number
  createTime?: string
}

export interface UserItem {
  id: number
  name: string
  username: string
  nickname: string
  email: string
  phone: string
  role: string
  status: '正常' | '禁用'
}

export interface AdminLogItem {
  id: number
  operatorId: number
  operatorName?: string
  action: string
  targetType?: string
  targetId?: number
  detail?: string
  requestMethod?: string
  requestUri?: string
  requestIp?: string
  result?: string
  errorMessage?: string
  durationMs?: number
  createTime?: string
}

export interface SensitiveWordItem {
  id: number
  word: string
  level: number
  status: number
  createdBy?: number
  createTime?: string
  updateTime?: string
}

export interface ContentReviewRecordItem {
  id: number
  resourceId?: number
  submitterId?: number
  reviewerId?: number
  reviewType: 'AUTO' | 'MANUAL'
  decision: string
  matchedWords?: string
  reason?: string
  contentTitle?: string
  createTime?: string
}

export interface LoginResponse {
  accessToken: string
  userId: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
}

export interface RegisterPayload {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
}

export interface ResourceSubmitPayload {
  title: string
  cat?: string
  categories?: string[]
  summary: string
  contentMarkdown?: string
  contentScene: ContentScene
  tags?: string
  tagNames?: string[]
  attachments?: UploadedFileItem[]
  files?: UploadedFileItem[]   // 临时兼容，submitResource 内部处理
}

export interface ResourceUpdatePayload {
  title: string
  cat?: string
  categories?: string[]
  summary: string
  contentMarkdown: string
  contentScene: ContentScene
  tags?: string
  tagNames?: string[]
  changeDescription?: string
  file?: UploadedFileItem | ResourceAttachmentItem
  attachments?: (UploadedFileItem | ResourceAttachmentItem)[]
}

export interface LoadOptions {
  force?: boolean
}

export interface HomeLoadOptions extends LoadOptions {
  includePersonal?: boolean
}

export interface ResourceDetailLoadOptions extends LoadOptions {
  includeFavorite?: boolean
}

export function mapStatus(status: number): ResourceStatus {
  if (status === 1) return '已发布'
  if (status === 2) return '待修改'
  if (status === 3) return '已拒绝'
  if (status === 4) return '已下架'
  return '待审核'
}

export function flattenCategories(items: CategoryApiItem[]): CategoryApiItem[] {
  return items.flatMap(item => [item, ...flattenCategories(item.children || [])])
}

export function mapUser(item: LoginUser): UserItem {
  return {
    id: item.userId,
    username: item.username,
    nickname: item.nickname || '',
    name: item.nickname || item.username,
    role: item.role === 'ADMIN' ? '管理员' : '学生',
    email: item.email || '',
    phone: item.phone || '',
    status: item.status === 0 ? '禁用' : '正常'
  }
}
