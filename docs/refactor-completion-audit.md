# CampusHub 九阶段重构完成审计

本文把任务说明中的 20 组要求、必测场景和 16 条验收标准逐项映射到仓库实现，作为阶段 1～9 文档的总索引。详细设计和配置变化见 `refactor-phase1-*` 至 `refactor-phase9-*`。

## 20 组要求映射

| # | 要求 | 落地证据 |
|---|---|---|
| 1 | MySQL 事务完整性 | `ResourceServiceImpl`、`ResourceVersionServiceImpl` 等核心写操作使用事务；`ResourceTransactionIntegrationTest` 覆盖创建、更新附件失败时整体回滚。 |
| 2 | MySQL/ES 最终一致性 | `V5__resource_outbox_messaging.sql`、`OutboxService`、`OutboxPublisher`、`ResourceIndexListener` 实现业务与 Outbox 同事务、提交后投递、重试和补偿。 |
| 3 | RabbitMQ 完整使用 | `RabbitMQConfig` 配置索引、审核通知、下载计数主队列及 DLQ；消费幂等表、手动确认、有限重试、失败指标和队列积压采集均已实现。 |
| 4 | Redis 缓存一致性 | 资源详情和分类统一由 Spring Cache/缓存服务管理；随机 TTL、空值短 TTL、互斥锁防击穿、写后失效和计数刷新失效由缓存测试覆盖。 |
| 5 | JWT 安全刷新 | Access/Refresh 类型、`jti`、Redis 会话、轮换、撤销、黑名单和 `tokenVersion` 已实现；刷新时以数据库最新用户与权限为准。 |
| 6 | 数据库 RBAC | `V4__database_driven_rbac.sql` 建立用户、角色、权限关系；支持多角色、多权限、权限缓存失效和最后一个超级管理员保护。 |
| 7 | 统一身份校验 | 网关先清理外部身份头并校验 JWT；下游继续使用同一公共 JWT 实现二次校验，不信任客户端身份头；本地内部服务默认仅监听回环地址。 |
| 8 | 用户/资源服务协作 | 用户服务提供批量公开资料接口；资源服务使用 OpenFeign 批量补全作者并带超时、熔断和占位降级，无列表 N+1 和作者硬编码。 |
| 9 | 资源版本 | `V6__resource_versions_taxonomy.sql`、版本服务和接口支持快照、列表、指定版本、附件快照、唯一版本号与回滚生成新版本。 |
| 10 | 分类和标签规范化 | 资源—分类、资源—标签关系表支持多对多；唯一标签、关系清理、频道组合筛选和 ES 名称快照已实现。 |
| 11 | Elasticsearch 搜索 | 多字段加权、中文兼容分析、过滤、排序、高亮、仅已审核资源、索引重建、一致性检查和删除同步已实现。 |
| 12 | 统一文件存储 | `V7__object_storage.sql` 和 `StoredObjectService` 支持 MinIO/本地同接口、持久化配额、UUID、扩展名/MIME/Magic Number、数量/容量限制、失败清理、引用生命周期及私有临时访问。 |
| 13 | 管理员日志持久化 | 管理日志写入 `t_admin_operation_log`，支持分页及操作人、类型、时间筛选；敏感字段统一脱敏，不记录密码、Token 和 Authorization。 |
| 14 | 内容审核 | `V8__content_moderation.sql`、敏感词服务和审核记录支持数据库加载、热更新、标题/摘要/正文/标签检查、自动/人工原因留痕及管理端维护。 |
| 15 | 分布式限流 | `DistributedRateLimit` 通过 Redis Lua 按用户/IP/接口/窗口执行；登录、注册、刷新、发布、上传、收藏、下载、搜索和审核已接入。 |
| 16 | 浏览/下载计数 | `V9__resource_counter_aggregation.sql` 和计数服务使用 Redis 聚合、短期去重、定时批量幂等写回，并失效详情缓存、通过 Outbox 刷新 ES。 |
| 17 | Nacos 与配置 | local 和非 local 配置分离；网关使用 `lb://`，Nacos、数据库、Redis、MQ、ES、MinIO 和 JWT 均支持环境变量；`.env.example` 只含示例值。 |
| 18 | Prometheus/Grafana | 三个服务均暴露采集端点；九项业务指标、JVM/HTTP/Hikari、RabbitMQ/Redis/ES exporter、Dashboard 和告警规则已配置。 |
| 19 | Docker Compose | MySQL、Redis、RabbitMQ、Elasticsearch、Nacos、MinIO、Prometheus、Grafana、exporter 和可选业务/前端 profile 已配置健康检查、命名卷、依赖及低内存覆盖文件。 |
| 20 | 接口和异常统一 | 使用统一 `Result`、错误码、全局异常、Bean Validation 和分页上限；Controller 响应通过 VO 隔离 Entity；Knife4j 声明 JWT Bearer，并在受保护接口标注认证要求。 |

## 强制测试场景

任务要求的注册登录、Access 认证、Refresh 刷新、角色变化后旧令牌失效、创建/更新事务回滚、审核权限、普通用户越权、详情缓存命中/失效、MQ 重复消费、ES 失败重试、删除索引、文件类型/大小、路径穿越、搜索状态过滤、版本创建/回滚，分别由下列测试覆盖：

- `UserServiceSecurityTest`、`TokenSessionServiceTest`、`RbacServiceIntegrationTest`
- `ResourceTransactionIntegrationTest`、`ResourceControllerTest`、`ResourceServiceCacheTest`
- `ResourceIndexListenerTest`、`OutboxIntegrationTest`、`OutboxPublisherTest`
- `ResourceFileControllerTest`、`FileValidationServiceTest`
- `ResourceSearchServiceTest`、`ResourceVersionTaxonomyIntegrationTest`

另外，`ControllerResponseContractTest` 防止数据库 Entity 再次进入公开响应模型；两个 `SecurityConfigTest` 校验 Knife4j 的 Bearer 认证定义。

## 16 条验收结论

| # | 验收标准 | 结论 |
|---|---|---|
| 1 | 可编译和启动 | 通过 Maven 全量测试/编译、Spring 上下文测试和前端生产构建验证。 |
| 2 | 前端主要功能可继续使用 | 保留原接口 JSON 合约；新增网关标签路由，前端测试和生产构建通过。 |
| 3 | 数据库失败不产生部分数据 | 通过事务集成测试验证。 |
| 4 | MySQL 成功、ES 失败可补偿 | Outbox 有限重试、失败状态、定时补偿和 DLQ 已验证。 |
| 5 | MQ 重复消费无重复数据 | 消费唯一键和重复消费测试通过。 |
| 6 | 角色变化后旧 Refresh 不保留权限 | 数据库回源、版本失效和轮换测试通过。 |
| 7 | 作者信息不硬编码 | 批量跨服务查询及降级测试通过。 |
| 8 | 管理日志重启后仍存在 | 日志已持久化至 MySQL。 |
| 9 | 浏览/下载量不会长期显示旧缓存 | Redis 聚合写回后清缓存并异步刷新 ES。 |
| 10 | 可查看版本并回滚 | 版本接口和集成测试通过。 |
| 11 | 分类标签关系可查询 | 多对多迁移、接口和集成测试通过。 |
| 12 | 文件经统一存储访问 | MinIO 私有对象、签名/代理访问和本地测试实现已验证。 |
| 13 | 搜索支持中文、多字段、过滤、排序和高亮 | 查询构造测试通过。 |
| 14 | Prometheus 可采集服务和业务指标 | 采集配置、指标端点和配置契约测试通过。 |
| 15 | Compose 可启动基础设施 | 标准及低内存 Compose 配置解析通过，服务均有健康检查。 |
| 16 | 仓库不含真实凭证 | 当前文件、Git 跟踪文件和历史定向扫描均未发现任务中暴露的真实数据库凭证；真实值必须仅放在未跟踪的 `.env` 或部署密钥中。 |

## 运维边界

- `docker-compose.yml` 的 Prometheus/Grafana 使用 Linux host network，以便在业务服务仅监听回环地址时仍可采集；README 已说明这一运行前提。
- MinIO、本地文件实现共用同一业务接口。本地实现只用于 local/test，生产配置必须启用 MinIO。
- 中文搜索采用不依赖外部 IK 插件的兼容分析方案；如果生产集群已安装 IK，可以在索引模板中切换为 IK。
- 实际部署前必须复制 `.env.example` 为未跟踪的 `.env` 并填写强随机密码和 JWT 密钥。
