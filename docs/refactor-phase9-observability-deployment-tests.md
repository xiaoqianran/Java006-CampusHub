# 阶段 9：监控、部署与测试

## 资源计数

浏览量按“资源 + 用户/IP”设置 30 分钟 Redis 去重键，浏览与下载增量写入
Redis Hash。默认每 10 秒原子轮换批次并批量写回 MySQL。

`t_counter_flush_batch` 与业务增量、Outbox 事件处于同一数据库事务中：

- 多实例同时处理同一批次时只允许一次写入。
- 数据库事务失败时 Redis 批次保留并重试。
- 数据库已提交但 Redis 删除失败时，幂等记录阻止重复增加。
- 写回成功后清理资源详情缓存，并由 Outbox 异步刷新 Elasticsearch 统计字段。

## 业务指标

资源服务暴露：

```text
resource_publish_total
resource_audit_total
resource_audit_reject_total
resource_search_total
resource_search_empty_total
resource_download_total
resource_upload_failure_total
rabbitmq_consume_failure_total
elasticsearch_sync_failure_total
```

## 基础设施监控

- Prometheus 采集 Gateway、User、Resource 三个服务。
- Redis exporter、RabbitMQ Prometheus 插件、Elasticsearch exporter。
- Grafana 自动加载 JVM/HTTP 与业务/基础设施 Dashboard。
- `docker/prometheus/alerts.yml` 提供服务下线、5xx、队列积压、Redis、ES 和
  HikariCP 告警。

## 部署

- Compose 服务均使用命名卷、内存限制、健康检查和启动依赖。
- Redis 强制从环境变量加载密码。
- `docker-compose.low-memory.yml` 提供低内存覆盖配置。
- `shiqian-frontend/Dockerfile` 使用 Node 构建并由 Nginx 提供静态文件。
- Flyway 在已有资源库上以版本 4 为基线，自动执行 V5 至当前迁移。

完整命令和低内存启动方式见根目录 `README.md`。
