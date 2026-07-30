# 阶段 5：Outbox、RabbitMQ 与 Elasticsearch 最终一致性

## 修改范围

- 资源创建、更新、删除、审核、恢复、重新提交和下载计数更新，在业务事务内写入 Outbox。
- 移除 `ResourceAfterCommitListener` 及进程内事件，不再在资源事务提交后直接调用 Elasticsearch。
- 新增索引同步、审核通知、下载计数三个 RabbitMQ 主队列及对应 DLQ。
- 新增 Outbox 定时发布、发布确认、指数退避、失败上限、陈旧占用恢复。
- 新增消息消费幂等记录、审核通知持久化和 RabbitMQ 队列积压指标。

## 数据库变更

迁移文件：`shiqian-resource/src/main/resources/db/migration/V5__resource_outbox_messaging.sql`

- `t_outbox_event`
  - 唯一 `message_id`
  - 事件类型：`RESOURCE_CREATED`、`RESOURCE_UPDATED`、`RESOURCE_DELETED`、`RESOURCE_AUDITED`
  - 状态：`PENDING`、`PUBLISHING`、`PUBLISHED`、`FAILED`、`DEAD`
  - 保存重试次数、下次重试时间、最近错误和发布时间
- `t_mq_consumed_message`
  - `(message_id, consumer_name)` 唯一，保障多个实例竞争消费时的业务幂等
- `t_user_notification`
  - 审核结果通知落库，`message_id` 唯一

新建库初始化、Docker MySQL 初始化和 H2 测试表结构已同步。

## 配置变更

- RabbitMQ 开启 correlated publisher confirm、mandatory return。
- 消费者 `acknowledge-mode=auto`：业务方法正常返回后确认；抛错时最多重试 3 次。
- `default-requeue-rejected=false`，重试耗尽后由 `resource.dlx` 路由至 DLQ。
- `resource.messaging.outbox.*` 支持批量大小、最大尝试次数、退避、占用超时和确认超时。
- `resource.messaging.monitor.*` 支持积压阈值和采集周期。
- 测试 profile 关闭调度与真实监听器启动。

## 一致性与幂等规则

1. MySQL 业务记录与 Outbox 事件同事务提交或回滚。
2. 多实例发布器通过条件更新将事件从可发布状态原子领取为 `PUBLISHING`。
3. Broker 确认且消息可路由后才标记 `PUBLISHED`。
4. 发布失败指数退避；达到上限进入 `DEAD`，不会无限重试。
5. ES 消费者始终读取 MySQL 当前状态，避免乱序旧消息覆盖新状态：
   - 已发布资源以资源 ID 为文档 ID 覆盖写入；
   - 不存在、逻辑删除或非发布状态均删除 ES 文档。
6. ES 操作失败不写消费记录，RabbitMQ 会重试；成功后的重复投递是安全覆盖。
7. 下载和审核通知在同一 MySQL 事务先竞争幂等唯一键，再执行计数或通知写入。

## 运维观测

- `rabbitmq.queue.messages{queue="..."}` 记录三个主队列和三个 DLQ 的消息数。
- 达到 `RABBIT_QUEUE_WARNING_THRESHOLD` 输出积压告警日志。
- Outbox 的 `FAILED`、`DEAD`、`last_error` 可用于故障排查和人工恢复。

## 验证覆盖

- 资源写入与 Outbox 同事务，以及附件失败时共同回滚。
- 四种资源事件类型和唯一消息 ID。
- Broker 发布确认后更新状态；失败退避；耗尽进入 `DEAD`。
- 审核事件同时发布索引和通知消息。
- 重复下载消息只增加一次计数。
- 重复审核消息只生成一条通知。
- 重复索引消息只执行一次；ES 失败不确认；资源删除后删除 ES 文档。
- 主队列的 DLX/DLQ 参数。
