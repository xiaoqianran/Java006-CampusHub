# 第一阶段：安全基线与事务边界

## 变更概要

- JWT 区分 `ACCESS` 与 `REFRESH`，增加 `jti` 和 `tokenVersion`。
- Refresh Token 仅以 SHA-256 摘要保存到 Redis，并采用一次性消费和轮换机制。
- 密码、角色、状态变更以及登出会提升用户令牌版本并撤销全部 Refresh Token。
- 当前 Access Token 在登出时进入 Redis 黑名单，黑名单键随令牌过期自动删除。
- 网关会移除客户端伪造的 `X-User-*` 请求头，并校验令牌类型、版本和黑名单。
- 用户、资源、附件、收藏和审核等核心写操作补充事务边界。
- MySQL 提交前不再直接写 Elasticsearch 或发送 RabbitMQ 消息；第一阶段使用提交后事件隔离故障，第五阶段将替换为持久化 Outbox。
- 管理员操作日志由内存列表改为 MySQL 持久化，并对请求参数中的敏感字段脱敏。
- 业务异常、参数异常和服务异常使用对应的 HTTP 状态码。
- 数据库密码、JWT 密钥和中间件凭据改为环境变量注入。

## 必需环境变量

复制仓库根目录的 `.env.example` 为 `.env`，至少设置：

- `MYSQL_ROOT_PASSWORD`
- `USER_DB_PASSWORD`
- `RESOURCE_DB_PASSWORD`
- `JWT_SECRET`（至少 32 字节随机字符串）
- `RABBITMQ_PASSWORD`

生产环境还应设置 Redis、Nacos 和 Elasticsearch 的认证信息。

## 数据库迁移

已有数据库在启动新版本前执行：

```bash
mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p \
  < docker/mysql/init/upgrade-security-phase1.sql
```

迁移会增加 `t_user.token_version` 并创建持久化管理员操作日志表。脚本可重复执行。

## 发布注意事项

- 新版本依赖 Redis 进行令牌会话校验；Redis 不可用时鉴权默认拒绝，属于安全失败关闭。
- 历史 JWT 不包含令牌类型和版本，发布后需要用户重新登录。
- 第五阶段已用持久化 Outbox、RabbitMQ 有限重试与 DLQ 替换第一阶段的临时提交后事件；
  当前事件可以在进程或中间件恢复后定时补偿。
