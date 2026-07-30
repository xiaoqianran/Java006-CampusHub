# 阶段 8：限流、内容审核与管理日志

## 修改范围

- `shiqian-common`：Redis Lua 固定窗口分布式限流注解与切面。
- `shiqian-user`：注册、登录、Refresh Token 限流。
- `shiqian-resource`：发布、上传、浏览、下载、收藏、搜索、审核限流。
- `shiqian-resource`：数据库敏感词、DFA 热更新、自动/人工审核记录。
- `shiqian-frontend`：内容安全管理页与持久化操作日志筛选。

## 数据库变更

- `V8__content_moderation.sql`
- `t_sensitive_word`
- `t_content_review_record`

敏感词变更后立即重建内存 DFA；自动拦截记录使用独立事务保存，因此资源
发布事务回滚时仍保留安全审计证据。普通错误响应只返回通用提示，命中词仅在
具有 `resource:audit` 权限的后台接口中显示。

## 配置

```dotenv
RATE_LIMIT_ENABLED=true
RATE_LIMIT_FAIL_OPEN=false
```

默认采用安全失败关闭。只有明确设置 `RATE_LIMIT_FAIL_OPEN=true` 时，Redis
故障才允许请求绕过限流。

## 验证

- `DistributedRateLimitAspectTest`：窗口内放行、429、Redis 故障关闭和显式降级。
- `ContentModerationIntegrationTest`：数据库热更新、四个内容域统一检查、审核记录区分。
- 前端生产构建和相关 Vitest 测试。
