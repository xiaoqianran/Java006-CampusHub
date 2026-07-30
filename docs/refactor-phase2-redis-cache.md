# 第二阶段：Redis 缓存治理

## 缓存键规范

缓存统一通过 Spring Cache 访问，不再在业务服务中直接使用 `RedisTemplate` 维护第二套数据。

```text
campushub:v1:cache:resource-detail::{resourceId}
campushub:v1:cache:category-tree::all
```

`v1` 是缓存结构版本。升级后旧键不会再被读取，可在低峰期删除：

```bash
redis-cli --scan --pattern 'resource:detail::*' | xargs -r redis-cli del
redis-cli del 'category:tree'
```

不要在共享 Redis 中执行 `FLUSHDB`。

## 一致性策略

- 资源创建、更新、删除、审核、重新提交、恢复和永久删除后清理对应详情缓存。
- 浏览量、下载量写入 MySQL 后立即清理详情缓存，下次读取回源并缓存最新计数。
- 分类新增、更新、删除事务提交后清理分类树缓存。
- 缓存写入与删除启用事务感知，数据库事务回滚时不执行缓存变更。
- Redis 故障时记录警告并降级到数据库，避免缓存故障导致页面整体不可用。

## 穿透、击穿与雪崩

- 资源不存在时缓存空值，默认 TTL 为 60 秒。
- 热点查询使用 `sync=true`，同一应用实例内只允许一个线程回源。
- Redis Cache Writer 使用锁模式，降低并发写入和清理冲突。
- 正常缓存 TTL 增加随机抖动，避免大量键同时过期。
- JSON 反序列化只允许项目类型及必要的 JDK 类型，不接受任意类。

## 配置

```text
CACHE_DEFAULT_TTL=15m
CACHE_RESOURCE_DETAIL_TTL=20m
CACHE_CATEGORY_TREE_TTL=60m
CACHE_NULL_TTL=60s
CACHE_TTL_JITTER=5m
```

空值抖动最多为其 TTL 的四分之一，保证空值始终保持短周期。

## 边界

`sync=true` 的回源互斥主要作用于单个服务实例。若后续压测证明跨实例热点击穿仍明显，可为极少数热点键增加按 Key 的分布式互斥；不应对所有读请求使用粗粒度全局锁。
