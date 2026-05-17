# “时迁”校园资源共享平台后端设计文档

## 1. 文档说明

本文档面向后端研发、前端联调、测试验收和部署运维，覆盖“时迁”校园资源共享平台从原型分析到接口设计、数据库设计、架构设计、技术选型、核心代码逻辑、自测联调、部署上线和日志排障的后端交付内容。

项目定位为高校学生资源共享服务平台，主要解决课程资料、学习笔记、实验报告、复习资料等校园学习资源的发布、分类、检索、收藏、下载和审核问题。

## 2. 原型图与业务分析

### 2.1 角色划分

| 角色 | 核心诉求 | 后端关注点 |
| --- | --- | --- |
| 未登录用户 | 浏览公开资源、搜索资源、查看分类 | 开放接口、分页查询、全文检索 |
| 普通学生 | 注册登录、上传资源、收藏资源、下载资源、维护个人资料 | JWT 鉴权、资源权限、数据归属校验 |
| 管理员 | 分类管理、资源审核、违规内容处理、运行状态查看 | RBAC 权限、审核流、操作日志、监控指标 |
| 运维/后端 | 服务部署、日志排查、性能监控、容量扩展 | Docker、Prometheus、Grafana、日志规范 |

### 2.2 核心业务流程

1. 用户注册/登录：用户提交账号密码，后端完成参数校验、密码加密、账号状态校验，并签发 JWT。
2. 资源发布：登录用户上传资源元信息，后端写入资源表，默认进入待审核状态。
3. 内容审核：管理员审核资源，通过后资源可被检索和下载，拒绝后不对外展示。
4. 分类管理：管理员维护多级资源分类，前端通过分类树渲染资源导航。
5. 资源检索：用户通过关键词、分类、分页条件查询资源；全文检索走 Elasticsearch。
6. 收藏/下载：用户收藏资源形成个人清单；下载行为通过 RabbitMQ 异步统计下载次数。
7. 监控排障：服务暴露健康检查和监控指标，结合日志快速定位异常接口、慢 SQL、MQ 积压等问题。

### 2.3 与“商品/购物车”模型的对应关系

如果从通用电商原型理解，资源平台可以做如下映射：

| 通用模型 | 本项目模型 | 说明 |
| --- | --- | --- |
| 用户表 | `t_user` | 平台账号、角色、状态、个人资料 |
| 商品表 | `t_resource` | 被共享的学习资源，包含标题、分类、文件地址、版本、审核状态 |
| 购物车表 | `t_favorite` / 规划 `t_resource_cart` | 当前已实现收藏清单；如需要“待下载清单/资源车”，可扩展为用户与资源的多对多关系表 |

## 3. 总体架构设计

### 3.1 微服务划分

| 模块 | 服务名 | 职责 |
| --- | --- | --- |
| `shiqian-gateway` | 网关服务 | 统一入口、路由转发、JWT 全局鉴权、跨域控制 |
| `shiqian-user` | 用户服务 | 注册、登录、用户信息维护、账号状态管理 |
| `shiqian-resource` | 资源服务 | 资源发布、分类管理、资源审核、收藏下载、全文检索 |
| `shiqian-common` | 公共模块 | 统一响应、异常处理、安全上下文、通用工具 |

### 3.2 架构分层

```text
前端 Vue3/Element Plus
        |
        v
Spring Cloud Gateway
        |
        +-- shiqian-user     -> MySQL(shiqian_user) / Redis
        |
        +-- shiqian-resource -> MySQL(shiqian_resource) / Redis / Elasticsearch / RabbitMQ
        |
        v
Nacos 服务注册与配置中心
        |
        v
Prometheus + Grafana 监控
```

### 3.3 关键设计

| 设计点 | 方案 |
| --- | --- |
| 服务治理 | 使用 Nacos 做服务注册发现和配置管理，服务通过 Spring Cloud 进行调用和治理 |
| 统一入口 | 所有前端请求先进入 Gateway，按路径转发到用户服务或资源服务 |
| 认证授权 | 登录后签发 JWT，网关和业务服务解析 Token，结合 Spring Security 做接口权限控制 |
| 数据隔离 | 用户库和资源库按服务拆分，避免跨服务直接改表 |
| 搜索能力 | MySQL 负责事务数据，Elasticsearch 负责资源标题/描述等全文检索 |
| 异步解耦 | 下载统计、审核通知等非主链路逻辑通过 RabbitMQ 异步处理 |
| 缓存优化 | 分类树、热门资源、资源详情等读多写少数据优先使用 Redis 缓存 |
| 可观测性 | Actuator 暴露健康检查和指标，Prometheus 采集，Grafana 展示服务状态 |

## 4. 技术选型

| 分类 | 技术 | 选型原因 |
| --- | --- | --- |
| 后端框架 | Spring Boot 3.2.0 | 生态成熟，适合快速构建 REST 服务 |
| 微服务 | Spring Cloud 2023.0.0 | 网关、服务治理、配置管理能力完整 |
| 注册配置 | Spring Cloud Alibaba Nacos | 支持服务注册、配置动态管理，适合微服务环境 |
| 安全框架 | Spring Security + JWT | 支持无状态鉴权、RBAC 权限控制 |
| ORM | MyBatis-Plus 3.5.5 | 简化 CRUD、分页、逻辑删除和自动填充 |
| 数据库 | MySQL 8.0 | 关系型数据存储稳定，支持事务和索引优化 |
| 缓存 | Redis | 提升热点数据访问性能，降低 DB 压力 |
| 搜索 | Elasticsearch | 支持资源标题、描述的全文检索和相关度排序 |
| 消息队列 | RabbitMQ | 下载统计、审核消息等异步任务削峰解耦 |
| 接口文档 | Knife4j / OpenAPI 3 | 便于前后端联调和接口验收 |
| 构建工具 | Maven | 多模块依赖管理清晰 |
| 容器化 | Docker / Docker Compose | 统一运行环境，便于本地和服务器部署 |
| 监控 | Prometheus + Grafana | 指标采集、可视化面板和告警扩展 |

## 5. 数据库设计

### 5.1 用户库 `shiqian_user`

#### `t_user` 用户表

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 用户 ID |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | 登录用户名 |
| `password` | VARCHAR(200) | NOT NULL | BCrypt 加密密码 |
| `nickname` | VARCHAR(50) | NOT NULL | 昵称 |
| `email` | VARCHAR(100) | NULL | 邮箱 |
| `phone` | VARCHAR(20) | NULL | 手机号 |
| `avatar` | VARCHAR(500) | NULL | 头像地址 |
| `role` | VARCHAR(20) | NOT NULL | 角色：`USER` / `ADMIN` |
| `status` | TINYINT | NOT NULL | 状态：0 禁用，1 正常 |
| `create_time` | DATETIME | NOT NULL | 创建时间 |
| `update_time` | DATETIME | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL | 逻辑删除：0 正常，1 删除 |

索引设计：

| 索引 | 字段 | 说明 |
| --- | --- | --- |
| `PRIMARY` | `id` | 主键查询 |
| `idx_username` | `username` | 登录、注册唯一性校验 |

### 5.2 资源库 `shiqian_resource`

#### `t_resource` 资源主表

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 资源 ID |
| `user_id` | BIGINT | NOT NULL | 上传者用户 ID |
| `title` | VARCHAR(200) | NOT NULL | 资源标题 |
| `description` | VARCHAR(1000) | NULL | 资源描述 |
| `category_id` | BIGINT | NULL | 分类 ID |
| `file_url` | VARCHAR(500) | NOT NULL | 文件地址 |
| `file_size` | BIGINT | NOT NULL | 文件大小，单位字节 |
| `file_type` | VARCHAR(100) | NULL | 文件类型 |
| `download_count` | INT | NOT NULL | 下载次数 |
| `version` | INT | NOT NULL | 资源版本号 |
| `status` | TINYINT | NOT NULL | 审核状态：0 待审核，1 已通过，2 已拒绝 |
| `create_time` | DATETIME | NOT NULL | 创建时间 |
| `update_time` | DATETIME | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL | 逻辑删除 |

索引设计：

| 索引 | 字段 | 说明 |
| --- | --- | --- |
| `PRIMARY` | `id` | 资源详情查询 |
| `idx_user_id` | `user_id` | 查询用户发布资源 |
| `idx_category_id` | `category_id` | 分类筛选 |

#### `t_category` 资源分类表

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 分类 ID |
| `parent_id` | BIGINT | NOT NULL | 父分类 ID，0 表示根节点 |
| `name` | VARCHAR(100) | NOT NULL | 分类名称 |
| `sort_order` | INT | NOT NULL | 排序值 |
| `icon` | VARCHAR(255) | NULL | 分类图标 |
| `status` | TINYINT | NOT NULL | 状态：0 禁用，1 启用 |
| `create_time` | DATETIME | NOT NULL | 创建时间 |
| `update_time` | DATETIME | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL | 逻辑删除 |

索引设计：

| 索引 | 字段 | 说明 |
| --- | --- | --- |
| `PRIMARY` | `id` | 分类详情查询 |
| `idx_parent_id` | `parent_id` | 构建分类树 |

#### `t_favorite` 资源收藏表

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 收藏记录 ID |
| `user_id` | BIGINT | NOT NULL | 用户 ID |
| `resource_id` | BIGINT | NOT NULL | 资源 ID |
| `create_time` | DATETIME | NOT NULL | 收藏时间 |

索引设计：

| 索引 | 字段 | 说明 |
| --- | --- | --- |
| `uk_user_resource` | `user_id`, `resource_id` | 防止重复收藏 |
| `idx_resource_id` | `resource_id` | 统计资源收藏情况 |

### 5.3 规划扩展表：资源车/待下载清单

如产品经理要求保留“购物车”交互，可新增 `t_resource_cart`，用于“加入待下载清单”“批量下载”“稍后学习”等场景。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT | NOT NULL | 用户 ID |
| `resource_id` | BIGINT | NOT NULL | 资源 ID |
| `checked` | TINYINT | NOT NULL | 是否选中：0 否，1 是 |
| `create_time` | DATETIME | NOT NULL | 加入时间 |
| `update_time` | DATETIME | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL | 逻辑删除 |

建议索引：

| 索引 | 字段 | 说明 |
| --- | --- | --- |
| `uk_user_resource` | `user_id`, `resource_id` | 防止重复加入 |
| `idx_user_id` | `user_id` | 查询用户清单 |

## 6. Elasticsearch 索引设计

资源全文检索索引建议命名为 `shiqian_resource`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | keyword/long | 资源 ID |
| `title` | text | 标题全文检索 |
| `description` | text | 描述全文检索 |
| `categoryId` | long | 分类过滤 |
| `userId` | long | 上传者过滤 |
| `fileType` | keyword | 文件类型过滤 |
| `downloadCount` | integer | 下载热度排序 |
| `status` | integer | 审核状态 |
| `createTime` | date | 发布时间排序 |

同步策略：

| 场景 | 同步动作 |
| --- | --- |
| 资源审核通过 | 写入或更新 ES 文档 |
| 资源编辑 | 更新 ES 文档 |
| 资源删除 | 删除 ES 文档 |
| 审核拒绝 | 从 ES 删除或设置不可见状态 |

## 7. Redis 缓存设计

| Key | 类型 | TTL | 说明 |
| --- | --- | --- | --- |
| `category:tree` | String/JSON | 30 分钟 | 分类树缓存 |
| `resource:detail:{id}` | String/JSON | 10 分钟 | 资源详情缓存 |
| `resource:hot:list` | ZSet/List | 10 分钟 | 热门资源列表 |
| `user:token:blacklist:{jti}` | String | Token 剩余有效期 | 退出登录或封禁场景 |
| `rate:resource:create:{userId}` | String/Counter | 1 分钟 | 发布资源限流 |

缓存一致性策略：

1. 查询优先读 Redis，未命中再读 MySQL，并回写缓存。
2. 更新、删除资源后主动删除对应缓存。
3. 分类新增、修改、删除后删除 `category:tree`。
4. 热门资源允许短时间最终一致，采用定时刷新或下载消息异步更新。

## 8. RabbitMQ 消息设计

| 交换机 | Routing Key | 队列 | 消息体 | 用途 |
| --- | --- | --- | --- | --- |
| `resource.topic.exchange` | `resource.download` | `resource.download.queue` | `resourceId`, `userId`, `timestamp` | 异步增加下载次数 |
| `resource.topic.exchange` | `resource.audit` | `resource.audit.queue` | `resourceId`, `status`, `operatorId`, `timestamp` | 审核状态变更通知/后续扩展 |

设计原则：

1. 下载接口只发送消息，不在主链路同步更新计数，降低接口响应时间。
2. 消费端保证幂等，按资源 ID 更新下载计数。
3. MQ 异常时记录错误日志，后续可接入死信队列和重试策略。

## 9. 接口规范

### 9.1 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 9.2 通用错误码

| code | 说明 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录或 Token 无效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 系统内部错误 |

### 9.3 请求头规范

| Header | 是否必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 登录接口否，业务接口是 | `Bearer {accessToken}` |
| `Content-Type` | POST/PUT 是 | `application/json` |

## 10. 接口文档

### 10.1 用户服务接口

#### 用户注册

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 用户注册 |
| 请求方式 | `POST` |
| 请求路径 | `/api/user/register` |
| 是否登录 | 否 |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 4-20 位字母、数字、下划线 |
| `password` | string | 是 | 6-32 位 |
| `nickname` | string | 否 | 昵称，最多 20 字 |
| `email` | string | 否 | 邮箱 |
| `phone` | string | 否 | 手机号 |

出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 注册成功返回空 |

#### 用户登录

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 用户登录 |
| 请求方式 | `POST` |
| 请求路径 | `/api/user/login` |
| 是否登录 | 否 |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 用户名 |
| `password` | string | 是 | 密码 |

出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | string | 访问令牌 |
| `refreshToken` | string | 刷新令牌 |
| `userId` | number | 用户 ID |
| `username` | string | 用户名 |
| `nickname` | string | 昵称 |
| `role` | string | 角色 |

#### 修改当前用户信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 修改当前用户信息 |
| 请求方式 | `PUT` |
| 请求路径 | `/api/user/me` |
| 是否登录 | 是 |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nickname` | string | 否 | 昵称 |
| `email` | string | 否 | 邮箱 |
| `phone` | string | 否 | 手机号 |
| `avatar` | string | 否 | 头像地址 |

出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 修改成功返回空 |

#### 用户服务健康检查

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 用户服务健康检查 |
| 请求方式 | `GET` |
| 请求路径 | `/api/user/health` |
| 是否登录 | 否 |

出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `service` | string | 服务名 |
| `status` | string | 服务状态 |
| `database` | string | 数据库连接状态 |
| `timestamp` | number | 时间戳 |

### 10.2 资源服务接口

#### 创建资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 创建资源 |
| 请求方式 | `POST` |
| 请求路径 | `/api/resource` |
| 是否登录 | 是 |
| 权限 | `resource:create` |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 是 | 资源标题，最多 200 字 |
| `description` | string | 否 | 资源描述，最多 1000 字 |
| `categoryId` | number | 是 | 分类 ID |
| `fileUrl` | string | 是 | 文件地址 |
| `fileSize` | number | 是 | 文件大小 |
| `fileType` | string | 是 | 文件类型 |

出参：`data = null`

#### 分页查询资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 分页查询资源 |
| 请求方式 | `GET` |
| 请求路径 | `/api/resource` |
| 是否登录 | 否 |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | number | 否 | 页码，默认 1 |
| `size` | number | 否 | 每页条数，默认 10 |
| `categoryId` | number | 否 | 分类 ID |
| `keyword` | string | 否 | 关键词 |

出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `records` | array | 资源列表 |
| `total` | number | 总条数 |
| `size` | number | 每页条数 |
| `current` | number | 当前页 |
| `pages` | number | 总页数 |

#### 查询资源详情

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询资源详情 |
| 请求方式 | `GET` |
| 请求路径 | `/api/resource/{id}` |
| 是否登录 | 否 |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | number | 是 | 资源 ID |

出参：`Resource` 资源对象。

#### 修改资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 修改资源 |
| 请求方式 | `PUT` |
| 请求路径 | `/api/resource/{id}` |
| 是否登录 | 是 |
| 权限 | `resource:update` |

入参与创建资源一致，路径参数 `id` 为资源 ID。后端需要校验资源归属或管理员权限。

#### 删除资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 删除资源 |
| 请求方式 | `DELETE` |
| 请求路径 | `/api/resource/{id}` |
| 是否登录 | 是 |
| 权限 | `resource:delete` |

入参：路径参数 `id`。

出参：`data = null`

#### 下载资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 下载资源 |
| 请求方式 | `POST` |
| 请求路径 | `/api/resource/{id}/download` |
| 是否登录 | 建议是，当前接口兼容匿名 |

业务说明：接口向 RabbitMQ 发送下载消息，由消费者异步增加下载次数。

#### 收藏资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 收藏资源 |
| 请求方式 | `POST` |
| 请求路径 | `/api/resource/{id}/favorite` |
| 是否登录 | 是 |

出参：`data = null`

#### 取消收藏资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 取消收藏资源 |
| 请求方式 | `DELETE` |
| 请求路径 | `/api/resource/{id}/favorite` |
| 是否登录 | 是 |

出参：`data = null`

#### 查询是否已收藏

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询是否已收藏 |
| 请求方式 | `GET` |
| 请求路径 | `/api/resource/{id}/favorite` |
| 是否登录 | 是 |

出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | boolean | 是否已收藏 |

#### 全文搜索资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 全文搜索资源 |
| 请求方式 | `GET` |
| 请求路径 | `/api/resource/search` |
| 是否登录 | 否 |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 是 | 搜索关键词 |
| `page` | number | 否 | 页码，默认 1 |
| `size` | number | 否 | 每页条数，默认 10 |

出参：Elasticsearch 分页结果。

#### 审核资源

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 审核资源 |
| 请求方式 | `PUT` |
| 请求路径 | `/api/resource/{id}/audit` |
| 是否登录 | 是 |
| 权限 | `resource:audit` |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | number | 是 | 资源 ID |
| `status` | number | 是 | 1 通过，2 拒绝 |

出参：`data = null`

### 10.3 分类服务接口

#### 新增分类

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 新增分类 |
| 请求方式 | `POST` |
| 请求路径 | `/api/category` |
| 是否登录 | 建议管理员 |

入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 分类名称 |
| `parentId` | number | 是 | 父分类 ID |
| `sortOrder` | number | 是 | 排序 |
| `icon` | string | 否 | 图标 |
| `status` | number | 是 | 0 禁用，1 启用 |

出参：`data = null`

#### 修改分类

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 修改分类 |
| 请求方式 | `PUT` |
| 请求路径 | `/api/category/{id}` |
| 是否登录 | 建议管理员 |

入参与新增分类一致。

#### 删除分类

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 删除分类 |
| 请求方式 | `DELETE` |
| 请求路径 | `/api/category/{id}` |
| 是否登录 | 建议管理员 |

出参：`data = null`

#### 查询分类详情

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询分类详情 |
| 请求方式 | `GET` |
| 请求路径 | `/api/category/{id}` |
| 是否登录 | 否 |

出参：`Category` 分类对象。

#### 查询分类树

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询分类树 |
| 请求方式 | `GET` |
| 请求路径 | `/api/category/tree` |
| 是否登录 | 否 |

出参：分类树数组，节点包含 `children`。

#### 分页查询分类

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 分页查询分类 |
| 请求方式 | `GET` |
| 请求路径 | `/api/category` |
| 是否登录 | 否 |

入参：`page`, `size`。

## 11. 核心代码逻辑设计

### 11.1 注册登录逻辑

1. Controller 接收 `RegisterDTO` / `LoginDTO` 并进行 JSR-303 参数校验。
2. Service 查询用户名是否存在。
3. 注册时使用 BCrypt 加密密码后写入 `t_user`。
4. 登录时校验账号状态和密码。
5. 登录成功生成 `accessToken`、`refreshToken`，返回用户基础信息。
6. 后续请求通过 `Authorization` Header 携带 Token。

### 11.2 JWT 鉴权逻辑

1. Gateway 全局过滤器解析请求路径。
2. 登录、注册、健康检查等白名单接口直接放行。
3. 非白名单接口校验 `Authorization`。
4. Token 有效时将用户 ID、角色、权限写入安全上下文。
5. 业务服务通过 `SecurityUtil.getCurrentUserId()` 获取当前用户。
6. 使用 `@PreAuthorize` 对资源创建、修改、删除、审核等接口做权限控制。

### 11.3 资源发布逻辑

1. 校验用户登录态。
2. 校验资源标题、分类、文件地址、文件大小、文件类型。
3. 敏感词过滤资源标题和描述。
4. 写入 `t_resource`，默认 `status=0` 待审核，`version=1`。
5. 删除相关缓存。
6. 审核通过后同步到 Elasticsearch。

### 11.4 资源搜索逻辑

1. 普通分页查询优先使用 MySQL，支持分类和关键词条件。
2. 全文搜索接口使用 Elasticsearch，对 `title`、`description` 做匹配。
3. 搜索结果按相关度、发布时间、下载次数等维度排序。
4. 对只允许展示审核通过资源的场景，需要追加 `status=1` 条件。

### 11.5 下载统计逻辑

1. 用户点击下载，接口构造 `ResourceDownloadMessage`。
2. 生产者发送消息到 RabbitMQ。
3. 消费者接收消息后校验资源是否存在。
4. 异步更新 `download_count = download_count + 1`。
5. 更新热门资源缓存或等待定时任务刷新。

### 11.6 收藏逻辑

1. 用户登录后调用收藏接口。
2. 后端校验资源存在。
3. 写入 `t_favorite`，通过唯一索引防止重复收藏。
4. 取消收藏时按 `user_id + resource_id` 删除记录。
5. 查询是否收藏时返回布尔值，方便前端按钮状态渲染。

## 12. 自测方案

### 12.1 后端单元测试

| 测试对象 | 测试内容 |
| --- | --- |
| Controller | 参数校验、响应结构、权限拦截 |
| Service | 注册登录、资源发布、分类树、收藏、审核 |
| Mapper | CRUD、分页、逻辑删除、唯一索引 |
| MQ Listener | 下载消息消费、异常消息处理 |
| Config | Redis、RabbitMQ、Security、MyBatis-Plus 配置 |

### 12.2 接口自测用例

| 用例 | 步骤 | 预期 |
| --- | --- | --- |
| 注册成功 | 调用注册接口，传入合法账号 | 返回 200，数据库新增用户 |
| 重复注册 | 使用相同 username 再次注册 | 返回业务错误 |
| 登录成功 | 调用登录接口 | 返回 accessToken |
| 未登录创建资源 | 不带 Token 调用创建资源 | 返回 401 |
| 发布资源 | 带 Token 创建资源 | 返回 200，资源状态为待审核 |
| 审核资源 | 管理员调用审核接口 | 状态变为通过/拒绝 |
| 搜索资源 | 调用搜索接口 | 返回分页资源 |
| 收藏资源 | 登录后收藏同一资源两次 | 第二次不产生重复记录 |
| 下载资源 | 调用下载接口 | MQ 消费后下载次数增加 |

### 12.3 推荐执行命令

```bash
mvn test
mvn clean package -DskipTests
```

## 13. 前后端联调说明

### 13.1 联调顺序

1. 后端启动 MySQL、Redis、RabbitMQ、Elasticsearch、Nacos。
2. 启动 `shiqian-gateway`、`shiqian-user`、`shiqian-resource`。
3. 前端配置统一 API Base URL 指向网关地址。
4. 前端先联调注册登录，拿到 Token 后统一写入请求拦截器。
5. 联调分类树、资源列表、资源详情。
6. 联调资源发布、收藏、下载、审核。
7. 联调异常场景：Token 失效、参数错误、无权限、资源不存在。

### 13.2 前端请求约定

1. 登录成功后缓存 `accessToken`。
2. 业务接口请求头统一追加 `Authorization: Bearer {accessToken}`。
3. `code != 200` 时展示 `message`。
4. 401 跳转登录页，403 展示无权限提示。
5. 分页组件使用后端返回的 `records`、`total`、`current`、`size`。

## 14. 部署上线方案

### 14.1 环境准备

| 组件 | 建议 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Redis | 6.x/7.x |
| RabbitMQ | 3.12+ |
| Elasticsearch | 8.x |
| Nacos | 2.x |
| Docker | 24+ |

### 14.2 部署步骤

1. 拉取代码并确认配置文件。
2. 初始化数据库：执行 `shiqian-user/src/main/resources/db/init.sql` 和 `shiqian-resource/src/main/resources/db/init.sql`。
3. 启动基础中间件：MySQL、Redis、RabbitMQ、Elasticsearch、Nacos。
4. 构建后端服务：`mvn clean package -DskipTests`。
5. 使用 Docker 或 Java 命令启动各服务。
6. 检查健康接口和 Actuator 指标。
7. 配置网关路由和前端 API 地址。
8. 执行冒烟测试：登录、资源列表、资源发布、搜索、收藏、下载。

### 14.3 上线检查清单

| 检查项 | 标准 |
| --- | --- |
| 配置隔离 | dev/test/prod 配置分离，敏感信息不提交仓库 |
| 数据库 | 表结构、索引、初始化数据执行成功 |
| 鉴权 | 白名单和受保护接口符合预期 |
| 日志 | 应用日志可检索，异常日志包含 trace 信息 |
| 监控 | 服务存活、JVM、接口耗时、错误率可观测 |
| MQ | 队列、交换机、绑定关系正常 |
| ES | 索引存在，资源审核通过后可搜索 |
| 回滚 | 保留上一版本镜像或 jar 包 |

## 15. 日志排查与 Bug 修复

### 15.1 日志规范

| 场景 | 日志级别 | 内容 |
| --- | --- | --- |
| 请求入口 | INFO | 用户 ID、接口、关键业务参数 |
| 业务异常 | WARN | 错误码、错误原因、业务 ID |
| 系统异常 | ERROR | 异常堆栈、traceId、请求路径 |
| MQ 消费 | INFO/WARN | 消息 ID、资源 ID、消费结果 |
| 外部依赖异常 | ERROR | Redis/ES/MQ/DB 异常详情 |

### 15.2 常见问题排查

| 问题 | 排查方向 | 修复方式 |
| --- | --- | --- |
| 登录失败 | 用户是否存在、密码是否 BCrypt 匹配、账号状态 | 修正账号数据或密码校验逻辑 |
| 401 未登录 | Token 是否携带、格式是否为 Bearer、密钥是否一致 | 修复前端请求头或后端 JWT 配置 |
| 403 无权限 | 用户角色、权限标识、`@PreAuthorize` 配置 | 补齐权限映射或调整接口权限 |
| 资源查不到 | 审核状态、逻辑删除、分类条件、分页参数 | 修复查询条件或数据状态 |
| 搜索无结果 | ES 索引是否存在、同步是否成功、分词是否符合预期 | 重建索引或补偿同步 |
| 下载次数不增加 | MQ 是否发送成功、队列是否积压、消费者是否异常 | 修复 MQ 配置或消费逻辑 |
| 分类树异常 | `parent_id` 是否正确、缓存是否过期 | 修复分类数据并清理缓存 |
| 接口慢 | 慢 SQL、缓存命中率、ES 查询耗时、线程池状态 | 加索引、优化查询、增加缓存 |

### 15.3 线上修复流程

1. 通过 Grafana 确认异常时间段和影响范围。
2. 根据 traceId 或用户 ID 检索应用日志。
3. 复现问题并定位 Controller、Service、Mapper 或中间件配置。
4. 编写或补充回归测试。
5. 修复代码并在测试环境验证。
6. 灰度发布或低峰期发布。
7. 观察错误率、接口耗时、MQ 积压和业务数据是否恢复。

## 16. 后续迭代建议

| 方向 | 建议 |
| --- | --- |
| 权限体系 | 从简单 `role` 扩展为 `user-role-permission` 标准 RBAC 表 |
| 资源车 | 新增 `t_resource_cart`，支持批量下载和稍后学习 |
| 文件存储 | 接入 MinIO/OSS，后端签发上传凭证 |
| 审核能力 | 增加敏感词命中记录、人工审核备注、审核日志表 |
| 搜索体验 | 支持高亮、分类聚合、文件类型筛选、热词推荐 |
| 消息可靠性 | 增加消息确认、重试、死信队列和消费幂等表 |
| 安全 | 增加接口限流、登录失败锁定、操作审计日志 |

