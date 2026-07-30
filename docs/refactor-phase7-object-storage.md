# 阶段 7：MinIO 与统一文件存储

## 目标

将上传逻辑从 Controller 直接写本机目录，迁移为统一对象存储服务。生产环境可使用私有
MinIO，本地与测试环境保留同一接口下的本地实现；历史
`/api/resource/files/{userId}/{fileName}` 地址继续只读兼容。

## 数据库变更

迁移脚本：`shiqian-resource/src/main/resources/db/migration/V7__object_storage.sql`

- `t_stored_object`：保存不可猜测的公开 ID、对象键、原始文件名、实际 MIME、所有者、
  资源绑定关系和生命周期状态。
- `t_user_storage_quota`：按用户持久化已用字节数；上传时锁定对应用户配额行，不再依赖
  JVM 内存计数。

用户首次使用新上传接口时，配额行会从现有资源/附件元数据初始化，避免升级后把历史文件
误当成 0 字节；之后所有增减都在数据库事务中维护。

对象状态：

```text
TEMPORARY -> BOUND -> ARCHIVED
                    \-> BOUND（版本回滚）
任意有效状态 -> PENDING_DELETE -> 物理对象与元数据删除
```

更新资源时，旧附件进入 `ARCHIVED`，确保历史版本回滚后文件仍可用；永久删除资源时，
资源全部历史对象统一清理并释放配额。未发布的临时上传默认 24 小时后清理。

## 上传安全

- 单文件大小、单次文件数和用户总容量均受配置限制。
- 原始文件名拒绝路径穿越，物理对象键只由服务端 UUID 生成。
- 同时校验扩展名、浏览器声明 MIME 和文件 Magic Number。
- PDF、图片、ZIP/Office Open XML、RAR、7z、旧版 Office 和 UTF-8 文本分别校验。
- 批量文件先全部验证，再开始写入；写对象或数据库失败会清理本批已写对象。
- 前端只获得 `/api/resource/files/object/{uuid}`，不会获得磁盘路径或 MinIO 对象键。

## 私有访问

- `TEMPORARY` 和 `ARCHIVED` 对象仅所有者或管理员可读。
- 只有绑定到已发布资源的 `BOUND` 对象允许匿名读取。
- 配置 `MINIO_PUBLIC_ENDPOINT` 后，访问接口会 302 到短时 MinIO 签名 URL。
- 未配置浏览器可达的 MinIO 域名时，资源服务代理输出文件，避免把容器内网地址返回浏览器。
- MinIO bucket 不配置公开策略，默认保持私有。

## 配置

`.env` 示例：

```dotenv
STORAGE_PROVIDER=minio
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_PUBLIC_ENDPOINT=https://files.example.com
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
MINIO_BUCKET=campushub-resources
STORAGE_SIGNED_URL_TTL=10m
STORAGE_TEMPORARY_TTL=24h
```

本地兜底：

```dotenv
STORAGE_PROVIDER=local
LOCAL_STORAGE_ROOT=uploads/objects
```

Docker Compose 已增加私有 MinIO 服务、健康检查和命名卷 `minio-data`。

## 验证

- `ResourceFileControllerTest`：文件类型/大小入口、Magic Number、MIME、路径穿越、
  批量原子验证、私有访问、发布后公开访问和元数据/配额持久化。
- `StoredObjectLifecycleIntegrationTest`：临时对象绑定、旧版本归档、版本回滚重新绑定、
  跨用户绑定拦截和删除释放配额。
