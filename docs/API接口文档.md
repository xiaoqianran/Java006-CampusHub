# 时迁校园资源共享平台 - 后端接口文档

> **版本**：v1.0  
> **生成日期**：2025  
> **维护团队**：后端研发  
> **适用对象**：前端工程师、测试、运维、对接方

---

## 1. 概述

“时迁”是面向高校的校园资源共享平台，提供学习资料、笔记、实验报告等资源的发布、分类、检索、收藏与下载能力。

本平台采用 **Spring Cloud 微服务架构**：

- **shiqian-gateway**（端口 8080）：统一入口、JWT 鉴权、路由转发
- **shiqian-user**（端口 8081）：用户注册、登录、资料维护
- **shiqian-resource**（端口 8082）：资源 CRUD、分类管理、收藏、ES 搜索、MQ 异步下载统计

所有外部请求必须经过网关，内部服务不直接暴露。

---

## 2. 技术栈与中间件

| 组件          | 版本/说明                  | 用途                     |
|---------------|----------------------------|--------------------------|
| Spring Boot   | 3.2.0                      | 基础框架                 |
| Spring Cloud Gateway | 2023.0.0              | 网关与鉴权               |
| MyBatis-Plus  | 3.5.5                      | ORM + 分页 + 逻辑删除    |
| Redis         | 7.x                        | 分类树、资源详情缓存     |
| RabbitMQ      | 3.13                       | 下载统计、审核消息异步   |
| Elasticsearch | 8.10                       | 资源全文检索             |
| MySQL         | 8.0                        | shiqian_user / shiqian_resource |
| Knife4j       | 4.3.0 (OpenAPI 3)          | 在线接口文档             |

---

## 3. 通用规范

### 3.1 统一响应格式

所有接口返回 `Result<T>` 结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

- `code`：业务状态码，200 表示成功
- `message`：提示信息
- `data`：业务数据，失败时通常为 null

### 3.2 错误码

| code | 含义                   | 说明                              |
|------|------------------------|-----------------------------------|
| 200  | 成功                   | 正常返回                          |
| 400  | 参数错误               | JSR-303 校验失败、非法参数        |
| 401  | 未登录 / Token 失效    | 缺少或无效 Authorization          |
| 403  | 无权限                 | 角色或权限不足                    |
| 404  | 资源不存在             | 用户/资源/分类不存在              |
| 405  | 方法不支持             | HTTP 方法错误                     |
| 500  | 业务/系统错误          | BusinessException 或未知异常      |

### 3.3 认证与授权

- **登录后接口** 必须在 Header 携带：
  ```
  Authorization: Bearer {accessToken}
  ```
- 网关白名单（无需 Token）：
  - `POST /api/user/register`
  - `POST /api/user/login`
  - `GET  /api/user/health`
  - `/actuator/**`
- 业务服务内部通过 `JwtAuthenticationFilter` 解析 Token，注入 `LoginUser` + 权限集合到 SecurityContext。
- 使用 `@PreAuthorize("hasAuthority('xxx')")` 进行方法级权限控制。

### 3.4 请求头与 Content-Type

- POST/PUT/PATCH：`Content-Type: application/json`
- 所有接口默认 UTF-8

### 3.5 分页约定

MyBatis-Plus `Page` 返回格式：

```json
{
  "records": [...],
  "total": 100,
  "size": 10,
  "current": 1,
  "pages": 10
}
```

---

## 4. 角色与权限体系

### 4.1 数据库驱动 RBAC

角色与权限分别保存在 `sys_role`、`sys_permission`，通过
`sys_user_role`、`sys_role_permission` 建立多对多关系。一个用户可以拥有多个角色，
Spring Security 每次认证读取共享 Redis 权限快照，缓存未命中时回源用户数据库。

| 内置角色 | 说明 |
|----------|------|
| USER | 默认角色，拥有日常资源权限 |
| ADMIN | 内容管理员，额外拥有资源审核和用户管理权限 |
| SUPER_ADMIN | 超级管理员，额外拥有 `rbac:manage` |

`role` 仍作为兼容字段出现在 Token 和部分前端响应中；真正的接口授权以 `roles`、
`permissions` 和 Spring Security authority 为准，不再使用 Java 硬编码角色映射。

### 4.2 RBAC 管理接口

以下接口统一要求 `rbac:manage`：

| 方法 | 路径 | 用途 |
|------|------|------|
| GET / POST | `/api/user/admin/rbac/roles` | 查询或创建角色 |
| PUT / DELETE | `/api/user/admin/rbac/roles/{roleId}` | 更新或删除自定义角色 |
| PUT | `/api/user/admin/rbac/roles/{roleId}/permissions` | 替换角色权限 |
| GET / POST | `/api/user/admin/rbac/permissions` | 查询或创建权限 |
| PUT / DELETE | `/api/user/admin/rbac/permissions/{permissionId}` | 更新或删除自定义权限 |
| PUT | `/api/user/admin/rbac/users/{userId}/roles` | 替换用户的多个角色 |

系统禁止删除内置角色/权限，也禁止移除或禁用最后一个启用的超级管理员。

---

## 5. 用户服务接口（/api/user/**）

**服务**：shiqian-user（8081）  
**Swagger**：http://localhost:8081/doc.html

### 5.1 健康检查

| 属性     | 值                                      |
|----------|-----------------------------------------|
| 方法     | `GET`                                   |
| 路径     | `/api/user/health`                      |
| 认证     | 否                                      |
| 权限     | 无                                      |

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "service": "shiqian-user",
    "status": "UP",
    "database": "CONNECTED",
    "timestamp": 1710000000000
  }
}
```

### 5.2 用户注册

| 属性     | 值                                      |
|----------|-----------------------------------------|
| 方法     | `POST`                                  |
| 路径     | `/api/user/register`                    |
| 认证     | 否                                      |

**请求体** `RegisterDTO`：
```json
{
  "username": "zhangsan",
  "password": "123456",
  "nickname": "张三",
  "email": "zs@example.com",
  "phone": "13800138000"
}
```

**校验规则**：
- username：4-20 位，`^[a-zA-Z0-9_]{4,20}$`
- password：6-32 位
- email：合法邮箱格式
- phone：`^1[3-9]\d{9}$`

**业务规则**：
- 用户名、邮箱、手机号全局唯一（未删除记录）
- 默认角色 `USER`，状态 1（正常）
- 密码 BCrypt 加密存储
- 昵称为空则使用 username

**错误示例**：用户名已存在、邮箱已被注册

### 5.3 用户登录

| 属性     | 值                                      |
|----------|-----------------------------------------|
| 方法     | `POST`                                  |
| 路径     | `/api/user/login`                       |
| 认证     | 否                                      |

**请求体** `LoginDTO`：
```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

**响应** `LoginVO`：
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1001,
  "username": "zhangsan",
  "nickname": "张三",
  "role": "USER",
  "roles": ["USER"],
  "permissions": ["resource:read", "resource:create"]
}
```

**业务规则**：
- 仅允许 status=1 的账号登录
- 签发 accessToken（2h）、refreshToken（7d）
- Token 包含 userId、username、role（HS256 签名）

### 5.4 更新当前用户信息

| 属性     | 值                                      |
|----------|-----------------------------------------|
| 方法     | `PUT`                                   |
| 路径     | `/api/user/me`                          |
| 认证     | 是                                      |

**请求体** `UpdateUserDTO`（全部可选）：
```json
{
  "nickname": "新昵称",
  "email": "new@example.com",
  "phone": "13900139000",
  "avatar": "https://xxx/avatars/1.jpg"
}
```

**业务规则**：
- 邮箱/手机号变更时校验唯一性
- 只更新非 null 字段

---

## 6. 资源服务接口

**服务**：shiqian-resource（8082）  
**Swagger**：http://localhost:8082/doc.html

### 6.1 资源管理（/api/resource）

#### 6.1.1 创建资源

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `POST`                                       |
| 路径     | `/api/resource`                              |
| 认证     | 是                                           |
| 权限     | `resource:create`                            |

**请求体** `ResourceCreateDTO`：
```json
{
  "title": "数据结构与算法笔记",
  "description": "2024春季学期完整笔记，含课后题解答",
  "categoryId": 12,
  "fileUrl": "https://oss.xxx.com/res/xxx.pdf",
  "fileSize": 2456789,
  "fileType": "application/pdf"
}
```

**响应**：`data = null`

**业务流程**：
1. 敏感词过滤（title + description）
2. 校验分类存在
3. 写入 t_resource，status=0（待审核）、version=1、downloadCount=0
4. 同步写入 Elasticsearch（index: `resource`）
5. 清除相关缓存

#### 6.1.2 分页查询资源列表

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `GET`                                        |
| 路径     | `/api/resource`                              |
| 认证     | 否（白名单）                                 |

**查询参数**：
- `page`（默认 1）
- `size`（默认 10）
- `categoryId`（可选）
- `keyword`（可选，模糊匹配 title/description）

**响应**：`Page<Resource>`（包含 userId、status 等完整字段）

**注意**：当前**不按 status 过滤**，会返回待审核资源。前端需自行处理展示逻辑。

#### 6.1.3 获取资源详情

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `GET`                                        |
| 路径     | `/api/resource/{id}`                         |
| 认证     | 否                                           |

**特性**：使用 Redis 缓存 `resource:detail:{id}`（10 分钟）

#### 6.1.4 更新资源

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `PUT`                                        |
| 路径     | `/api/resource/{id}`                         |
| 认证     | 是                                           |
| 权限     | `resource:update`                            |

**请求体**：同 `ResourceCreateDTO`

**业务逻辑**：
- 敏感词校验 + 分类校验
- version 自增
- 同步更新 Elasticsearch
- **当前未校验资源归属**（持有 update 权限即可修改任意资源）

#### 6.1.5 删除资源

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `DELETE`                                     |
| 路径     | `/api/resource/{id}`                         |
| 认证     | 是                                           |
| 权限     | `resource:delete`                            |

**业务规则**：**仅资源上传者本人**可删除（Service 层校验 `userId`）。

#### 6.1.6 下载资源（异步统计）

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `POST`                                       |
| 路径     | `/api/resource/{id}/download`                |
| 认证     | 否（匿名也允许，内部 userId 默认为 1）       |

**实现**：
- 立即返回 200
- 向 RabbitMQ 发送 `resource.download` 消息
- 消费者异步执行 `download_count + 1`

**幂等性**：消费者未做严格幂等，短时间内重复下载会多次计数。

#### 6.1.7 收藏资源

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `POST`                                       |
| 路径     | `/api/resource/{id}/favorite`                |
| 认证     | 是                                           |

**唯一约束**：t_favorite (user_id, resource_id) 防止重复收藏。

#### 6.1.8 取消收藏

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `DELETE`                                     |
| 路径     | `/api/resource/{id}/favorite`                |
| 认证     | 是                                           |

#### 6.1.9 查询是否已收藏

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `GET`                                        |
| 路径     | `/api/resource/{id}/favorite`                |
| 认证     | 是                                           |

**响应**：`data: true/false`

#### 6.1.10 全文搜索资源

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `GET`                                        |
| 路径     | `/api/resource/search`                       |
| 认证     | 否                                           |

**查询参数**：
- `keyword`（必填）
- `page` / `size`

**实现**：Elasticsearch multi_match 查询 title + description

**返回**：`Page<ResourceDocument>`（字段较少，无 downloadCount、createTime 等）

**注意**：搜索结果**不区分 status**，待审核资源也可能被搜到。

#### 6.1.11 审核资源（管理员）

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `PUT`                                        |
| 路径     | `/api/resource/{id}/audit`                   |
| 认证     | 是                                           |
| 权限     | `resource:audit`                             |

**查询参数**：
- `status`：1（通过） / 2（拒绝）

**副作用**：
- 更新 MySQL status
- 发送 `resource.audit` 消息到 RabbitMQ（当前消费者仅记录日志）

---

### 6.2 分类管理（/api/category）

**所有分类接口默认无需登录即可访问**（SecurityConfig permitAll）。

#### 6.2.1 新增分类

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `POST`                                       |
| 路径     | `/api/category`                              |

**请求体** `CategoryDTO`：
```json
{
  "name": "计算机科学",
  "parentId": 0,
  "sortOrder": 10,
  "icon": "https://xxx/icon/cs.png",
  "status": 1
}
```

**业务规则**：
- parentId=0 为一级分类
- 新增/修改/删除后自动清空 `category:tree` 缓存

#### 6.2.2 更新分类

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `PUT`                                        |
| 路径     | `/api/category/{id}`                         |

#### 6.2.3 删除分类

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `DELETE`                                     |
| 路径     | `/api/category/{id}`                         |

**约束**：存在子分类时禁止删除。

#### 6.2.4 获取分类详情

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `GET`                                        |
| 路径     | `/api/category/{id}`                         |

#### 6.2.5 获取分类树（推荐）

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `GET`                                        |
| 路径     | `/api/category/tree`                         |

**特性**：
- 仅返回 status=1 且未删除的分类
- 按 sortOrder 升序
- 递归构建 children 树结构
- Redis 缓存 `category:tree`（30 分钟）

#### 6.2.6 分页查询分类

| 属性     | 值                                           |
|----------|----------------------------------------------|
| 方法     | `GET`                                        |
| 路径     | `/api/category`                              |

**参数**：`page`、`size`

---

## 7. 数据库表结构摘要

### 7.1 shiqian_user

| 字段         | 类型          | 说明                     |
|--------------|---------------|--------------------------|
| id           | BIGINT PK     |                          |
| username     | VARCHAR(50)   | UNIQUE                   |
| password     | VARCHAR(200)  | BCrypt                   |
| nickname     | VARCHAR(50)   |                          |
| status       | TINYINT       | 0=禁用 1=正常            |
| token_version| BIGINT        | 令牌撤销版本             |
| deleted      | TINYINT       | 逻辑删除                 |

用户主表为 `sys_user`；角色权限表为 `sys_role`、`sys_permission`、
`sys_user_role`、`sys_role_permission`。旧 `t_user.role` 由
`V4__database_driven_rbac.sql` 迁移后删除。

### 7.2 shiqian_resource 库

- **t_resource**：资源主表（含 download_count、status、version）
- **t_category**：多级分类（parent_id=0 为根）
- **t_favorite**：用户收藏（user_id + resource_id 唯一）

---

## 8. 缓存策略

| Key                    | 类型     | TTL      | 失效场景                     |
|------------------------|----------|----------|------------------------------|
| category:tree          | String   | 30min    | 新增/修改/删除分类           |
| resource:detail:{id}   | String   | 10min    | 更新、删除资源               |
| resource:hot:list      | ZSet     | 10min    | 未实现（规划中）             |

**缓存一致性**：写操作后主动 `@CacheEvict`。

---

## 9. 消息队列（RabbitMQ）

**交换机**：`resource.topic`（Topic）

| RoutingKey       | Queue                    | 生产者             | 消费者                  | 用途               |
|------------------|--------------------------|--------------------|-------------------------|--------------------|
| resource.download| resource.download.queue  | 下载接口           | ResourceDownloadListener| 异步 +1 下载次数   |
| resource.audit   | resource.audit.queue     | 审核接口           | ResourceAuditListener   | 审核通知（当前仅日志） |

---

## 10. Elasticsearch 索引

**索引名**：`resource`

字段：
- title / description（text，standard analyzer）
- categoryId、userId、fileType、status（keyword / long / integer）

**同步时机**：
- 创建资源时立即写入
- 更新资源时覆盖
- 删除资源时删除文档

**当前局限**：搜索接口不强制过滤 status=1。

---

## 11. 敏感词过滤

基于 DFA 算法实现（shiqian-common）。

- 默认敏感词：违规,敏感词,广告（可通过配置 `content.sensitive-words` 扩展）
- 命中时抛出 `BusinessException("资源内容包含敏感词")`

---

## 12. 异常与日志

- 所有业务异常统一由 `GlobalExceptionHandler` 处理，返回 200 + 对应 code
- 关键日志已打在 Controller/Service/MQ 各层，包含 userId、resourceId、trace 信息
- 推荐使用 traceId 串联全链路排查

---

## 13. 在线文档与调试

| 服务         | Knife4j 地址                          |
|--------------|---------------------------------------|
| 网关         | http://localhost:8080/doc.html        |
| 用户服务     | http://localhost:8081/doc.html        |
| 资源服务     | http://localhost:8082/doc.html        |

**推荐**：通过网关访问文档，体验完整鉴权流程。

---

## 14. 前后端联调 checklist

1. 启动中间件（MySQL、Redis、RabbitMQ、ES、Nacos 可选）
2. 启动 gateway → user → resource
3. 注册 → 登录 → 获取 Token → 写入 localStorage
4. 先联调分类树、资源列表、详情（无需登录）
5. 联调发布资源（带 Token）
6. 联调收藏、下载、搜索
7. 管理员账号联调审核接口
8. 验证异常场景：无 Token、权限不足、敏感词、重复收藏

---

## 15. 已知设计/实现注意点

1. **资源更新权限过宽**：持有 `resource:update` 的任意用户可修改他人资源，建议后续增加归属或管理员校验。
2. **搜索与列表未过滤 status**：待审核资源仍可被普通用户搜索到，建议增加 `status=1` 默认过滤（或提供 `includePending` 参数给管理员）。
3. **下载计数非幂等**：同一用户短时间重复下载会多次增加计数。
4. **ES 文档字段不全**：搜索返回的 ResourceDocument 缺少 downloadCount、createTime 等，需二次查询或扩展映射。
5. **刷新 Token 未实现**：LoginVO 包含 refreshToken，但后端暂无 `/refresh` 接口。

---

## 16. 后续演进建议

- 完善 RBAC 细粒度权限表（user-role-permission）
- 资源车 / 批量下载功能
- MinIO / OSS 文件直传 + 签名
- 审核日志表 + 敏感词命中记录
- ES 高亮、聚合筛选、热词
- 接口限流（Sentinel）、登录失败锁定
- 统一错误码枚举全量使用

---

**文档维护**：后端新增接口或修改权限时，请同步更新本文件及 Swagger 注解。

---

*本文档由专业后端研发基于实际代码（Controller + Service + Config）逆向生成，力求 100% 反映当前运行时行为。*
