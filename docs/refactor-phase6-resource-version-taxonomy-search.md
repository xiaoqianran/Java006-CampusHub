# 阶段 6：资源版本、分类标签与搜索增强

## 数据库变更

执行迁移：

`shiqian-resource/src/main/resources/db/migration/V6__resource_versions_taxonomy.sql`

新增表：

- `t_resource_version`：资源正文、频道、分类、标签和附件元数据的完整版本快照，`(resource_id, version_number)` 唯一。
- `t_tag`：唯一标签字典。
- `t_resource_category`：资源与分类多对多关系。
- `t_resource_tag`：资源与标签多对多关系。

旧字段 `t_resource.category_id` 和 `t_resource.tags` 暂时保留为兼容镜像。迁移会回填历史单分类关系；历史资源首次查询版本或首次更新时自动建立初始快照。

## 核心行为

- 创建资源生成 v1；更新生成下一个版本。
- 版本快照包含附件元数据，回滚会同时恢复正文、分类、标签和附件。
- 回滚自身生成新版本，并把资源重新置为待审核状态。
- 分类、标签均可多选且非必填；删除时事务内清理关系和旧字段镜像。
- 分类或标签名称变化后写入 Outbox，由 RabbitMQ 重新同步 Elasticsearch。
- 永久删除资源同时清理分类、标签和版本关系。

## 接口

- `GET /api/resource/{id}/versions`
- `GET /api/resource/{id}/versions/{version}`
- `POST /api/resource/{id}/versions/{version}/rollback`
- `GET|POST /api/tag`
- `PUT|DELETE /api/tag/{id}`
- `POST /api/resource/index/rebuild`
- `GET /api/resource/index/consistency`

资源列表和搜索接口新增 `tagId`、`tag` 参数；可与 `categoryId`、`scene` 组合。

## Elasticsearch

索引文档新增摘要、正文、分类/标签 ID 与名称、作者、浏览/下载量和时间字段。中文字段使用内置 `cjk` 分析器，避免部署环境强依赖 IK 插件。标题权重最高，搜索仅返回已发布资源，并支持：

- 标题、摘要、描述、正文、分类名和标签多字段检索；
- 安全高亮；
- 分类、标签、频道和状态过滤；
- 相关度、创建时间、浏览量和下载量排序；
- 管理端索引重建和定时一致性检查。

旧 `resource` 索引需要通过管理接口重建一次，以应用新映射。

## 前端

- 发布和编辑页支持多分类、可创建多标签及修改说明。
- 发现页可组合选择频道、分类和标签。
- 搜索结果显示经过转义的高亮片段。
- 资源作者和管理员可在详情页查看版本正文/附件并执行回滚。
- 后台新增标签管理入口。

## 配置

- `RESOURCE_SEARCH_CONSISTENCY_INTERVAL_MS`：一致性检查周期，默认 1 小时。
- `RESOURCE_SEARCH_CONSISTENCY_INITIAL_DELAY_MS`：启动后首次检查延迟，默认 10 分钟。
