# GitHub Pages 前端 + Caddy 后端部署

本文针对 `Java-006-CampusHub` 的前后端分离部署：

- 前端：GitHub Pages
- API：`https://api.xiaoqianran.xyz`
- Caddy：TLS + 反向代理
- API 网关：`shiqian-gateway`（本机 `8080`）

> 认证安全模型已经调整：Access Token 仅保存在页面内存，Refresh Token 仅保存在 `HttpOnly` Cookie。浏览器请求 API 必须携带 credentials，并由 Gateway 统一处理精确 Origin CORS。

## 推荐域名结构

| 用途 | 示例 |
|---|---|
| GitHub Pages 默认域名 | `https://<user>.github.io/Java-006-CampusHub/` |
| 自定义前端域名 | `https://shiqian.xiaoqianran.xyz` |
| 后端 API 域名 | `https://api.xiaoqianran.xyz` |

前端 API 地址：

```text
https://api.xiaoqianran.xyz
```

## 服务端口

| 服务 | 端口 | 公网暴露 |
|---|---:|---|
| `shiqian-gateway` | 8080 | 仅通过 Caddy |
| `shiqian-user` | 8081 | 否 |
| `shiqian-resource` | 8082 | 否 |
| MySQL | 3306 | 否 |
| Redis | 6379 | 否 |
| Nacos | 8848 | 否 |
| Elasticsearch | 9200 | 否 |
| RabbitMQ | 5672 / 15672 | 否 |

## Caddyfile

CORS 已由 `shiqian-gateway` 统一处理，Caddy **不要再次写 `Access-Control-*` 响应头**，否则容易产生重复/冲突头。

```caddyfile
api.xiaoqianran.xyz {
    encode gzip zstd
    reverse_proxy 127.0.0.1:8080
}
```

重载：

```bash
caddy reload --config /etc/caddy/Caddyfile
```

## 认证 Cookie 与 Origin 配置

### 场景 A：GitHub Pages 默认域名

例如：

```text
Frontend: https://xiaoqianran.github.io
API:      https://api.xiaoqianran.xyz
```

两者属于跨站点。生产环境：

```bash
CORS_ALLOWED_ORIGINS=https://xiaoqianran.github.io
BROWSER_AUTH_ALLOWED_ORIGINS=https://xiaoqianran.github.io
REFRESH_TOKEN_COOKIE_SECURE=true
REFRESH_TOKEN_COOKIE_SAME_SITE=None
```

`SameSite=None` 必须和 `Secure=true` 一起使用。

### 场景 B：自定义同站前端子域

例如：

```text
Frontend: https://shiqian.xiaoqianran.xyz
API:      https://api.xiaoqianran.xyz
```

推荐：

```bash
CORS_ALLOWED_ORIGINS=https://shiqian.xiaoqianran.xyz
BROWSER_AUTH_ALLOWED_ORIGINS=https://shiqian.xiaoqianran.xyz
REFRESH_TOKEN_COOKIE_SECURE=true
REFRESH_TOKEN_COOKIE_SAME_SITE=Lax
```

如果需要同时允许多个前端 Origin，使用英文逗号分隔：

```bash
CORS_ALLOWED_ORIGINS=https://xiaoqianran.github.io,https://shiqian.xiaoqianran.xyz
BROWSER_AUTH_ALLOWED_ORIGINS=https://xiaoqianran.github.io,https://shiqian.xiaoqianran.xyz
```

禁止配置：

```text
CORS_ALLOWED_ORIGINS=*
```

因为本项目启用了 credential cookie，`*` 既不安全，也与 credential CORS 语义冲突。

## JWT 建议

生产环境至少显式配置：

```bash
JWT_SECRET=<高熵随机密钥，至少 32 字节>
JWT_ACCESS_TOKEN_EXPIRATION=1800000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
```

默认 Access Token 为 30 分钟，Refresh Token 为 7 天。Refresh Token 不会出现在登录/刷新 JSON 中，也不会进入 `localStorage`。

## GitHub Pages 配置

仓库使用：

```text
.github/workflows/deploy-frontend-pages.yml
```

在：

```text
Settings -> Secrets and variables -> Actions -> Variables
```

设置：

```text
VITE_API_BASE_URL=https://api.xiaoqianran.xyz
```

然后在：

```text
Settings -> Pages
```

选择 GitHub Actions 作为部署源。

## Pages 路径

默认项目 Pages 地址使用：

```yaml
VITE_BASE: /${{ github.event.repository.name }}/
```

如果绑定自定义域名并从域名根目录访问，可将构建路径设置为：

```yaml
VITE_BASE: /
```

## 运行时 API 地址

前端也支持：

```js
window.__SHIQIAN_CONFIG__ = {
  apiBaseUrl: 'https://api.xiaoqianran.xyz'
}
```

对应：

```text
shiqian-frontend/public/config.js
```

Pages 场景优先使用 Actions Variables。

## 本地开发

默认 Vite 代理目标：

```text
http://localhost:8080
```

也可指定：

```bash
cd shiqian-frontend
VITE_API_PROXY_TARGET=https://api.xiaoqianran.xyz npm run dev -- --host 0.0.0.0
```

本地 `shiqian-user` profile 会把 Refresh Cookie 的 `Secure` 关闭，以允许 `http://localhost` 开发；不要把 local profile 用于生产。

## 上线检查清单

1. `shiqian-user`、`shiqian-resource`、数据库、Redis、Nacos 等内部端口不对公网开放。
2. Caddy 只反向代理到 Gateway `127.0.0.1:8080`。
3. `JWT_SECRET`、`INTERNAL_SERVICE_KEY` 使用独立高熵随机值。
4. `CORS_ALLOWED_ORIGINS` 与 `BROWSER_AUTH_ALLOWED_ORIGINS` 都是精确 HTTPS Origin，不能是 `*`。
5. GitHub Pages 默认域名部署使用 `REFRESH_TOKEN_COOKIE_SAME_SITE=None` + `REFRESH_TOKEN_COOKIE_SECURE=true`。
6. 自定义同站子域优先使用 `SameSite=Lax` + `Secure=true`。
7. 浏览器 Network 中登录响应应出现 `Set-Cookie: campushub_refresh=...; HttpOnly; Secure; ...`。
8. 登录/刷新响应 JSON 中不得出现 `refreshToken`。
9. Application -> Local Storage 中不得出现 `shiqian_access_token` 或 `shiqian_refresh_token`。
10. 刷新页面后，应通过一次 `/api/user/refresh` Cookie 请求恢复登录，而不是从 Web Storage 恢复 token。
