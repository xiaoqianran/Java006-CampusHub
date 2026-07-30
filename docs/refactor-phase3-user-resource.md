# 第三阶段：用户与资源服务协作

## 调用链

资源列表先从资源数据库一次取出资源，再把页面内去重后的作者 ID 通过
OpenFeign 一次发送给用户服务：

```text
资源列表查询
→ 收集并去重 userId（每页最多 100）
→ POST /internal/users/public-profiles/batch
→ 回填 username、nickname、avatar
```

收藏列表、搜索结果、回收站、个人资源和资源详情共用同一个作者富化服务，
业务代码中不再维护用户 ID 到昵称的硬编码映射，也不会逐条调用用户服务。

## 内部接口安全

- Gateway 不路由 `/internal/**`。
- 用户服务内部接口要求 `X-CampusHub-Internal-Key`。
- 两个服务从同一个 `INTERNAL_SERVICE_KEY` 环境变量读取凭据。
- 凭据为空、缺失或不匹配时返回 403。
- 接口仅返回用户 ID、用户名、昵称和头像，不返回密码、联系方式、角色或状态。
- 生产环境不要把用户服务的 8081 端口暴露到公网。

建议生成至少 32 字节的随机值：

```bash
openssl rand -hex 32
```

## 超时与降级

```text
USER_CLIENT_CONNECT_TIMEOUT_MS=1000
USER_CLIENT_READ_TIMEOUT_MS=2000
```

OpenFeign 启用 Resilience4j 熔断器。连接失败、读取超时、非成功响应或返回数据
异常时，资源接口仍返回资源数据，并把缺失作者显示为 `用户#{userId}`。降级不读取
用户库，也不产生跨服务 N+1 请求。

本地 profile 默认直连 `http://localhost:8081`；其他环境默认通过服务名
`shiqian-user` 和 Nacos 发现，可用 `USER_SERVICE_URL` 显式覆盖。
