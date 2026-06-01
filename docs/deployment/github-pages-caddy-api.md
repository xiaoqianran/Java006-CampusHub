# GitHub Pages 前端 + Caddy 后端部署

本文只针对本项目 `Java006-CampusHub` 的前后端分离部署：

- 前端：GitHub Actions 构建 Vue 项目，并部署到 GitHub Pages
- 后端：运行在你的服务器上
- 服务器入口：Caddy + 后端 API 域名
- 后端网关：`shiqian-gateway`，本机端口 `8080`

## 推荐域名结构

建议用一个独立 API 子域名给后端：

| 用途 | 示例 |
|------|------|
| GitHub Pages 前端 | `https://<你的GitHub用户名>.github.io/Java006-CampusHub/` |
| GitHub Pages 自定义前端域名 | `https://shiqian.xiaoqianran.xyz` |
| 后端 API 域名 | `https://api.xiaoqianran.xyz` |

前端所有接口请求都访问：

```text
https://api.xiaoqianran.xyz/api/...
```

后端服务器上，Caddy 只需要把 `api.xiaoqianran.xyz` 反代到网关：

```text
127.0.0.1:8080
```

## 服务端口

| 服务 | 端口 | 是否建议公网暴露 | 说明 |
|------|------|------------------|------|
| `shiqian-gateway` | `8080` | 是，仅通过 Caddy 暴露 | 前端所有 `/api/*` 请求进入这里 |
| `shiqian-user` | `8081` | 否 | 网关内部转发用户接口 |
| `shiqian-resource` | `8082` | 否 | 网关内部转发资源和分类接口 |
| MySQL | `3306` | 否 | 数据库 |
| Redis | `6379` | 否 | 缓存 |
| Nacos | `8848` | 否 | 服务发现/配置 |
| Elasticsearch | `9200` | 否 | 搜索 |
| RabbitMQ | `5672` / `15672` | 否 | 消息队列和管理页 |

## 服务器 Caddyfile

下面是后端 API 域名的 Caddy 配置。把 `Access-Control-Allow-Origin` 改成你的 GitHub Pages 前端地址。

如果你使用 GitHub Pages 默认地址：

```text
https://<你的GitHub用户名>.github.io
```

如果你给 GitHub Pages 绑定了自定义域名，例如：

```text
https://shiqian.xiaoqianran.xyz
```

就填这个自定义域名。

```caddyfile
# ====================== shiqian api ======================
api.xiaoqianran.xyz {
    encode gzip zstd

    @preflight method OPTIONS
    respond @preflight 204

    header {
        Access-Control-Allow-Origin "https://<你的前端Pages域名>"
        Access-Control-Allow-Methods "GET,POST,PUT,DELETE,OPTIONS"
        Access-Control-Allow-Headers "Authorization,Content-Type"
        Access-Control-Max-Age "86400"
        Vary "Origin"
    }

    reverse_proxy 127.0.0.1:8080
}
```

示例：如果前端是 GitHub Pages 默认地址：

```caddyfile
api.xiaoqianran.xyz {
    encode gzip zstd

    @preflight method OPTIONS
    respond @preflight 204

    header {
        Access-Control-Allow-Origin "https://你的GitHub用户名.github.io"
        Access-Control-Allow-Methods "GET,POST,PUT,DELETE,OPTIONS"
        Access-Control-Allow-Headers "Authorization,Content-Type"
        Access-Control-Max-Age "86400"
        Vary "Origin"
    }

    reverse_proxy 127.0.0.1:8080
}
```

示例：如果前端 GitHub Pages 绑定自定义域名 `shiqian.xiaoqianran.xyz`：

```caddyfile
api.xiaoqianran.xyz {
    encode gzip zstd

    @preflight method OPTIONS
    respond @preflight 204

    header {
        Access-Control-Allow-Origin "https://shiqian.xiaoqianran.xyz"
        Access-Control-Allow-Methods "GET,POST,PUT,DELETE,OPTIONS"
        Access-Control-Allow-Headers "Authorization,Content-Type"
        Access-Control-Max-Age "86400"
        Vary "Origin"
    }

    reverse_proxy 127.0.0.1:8080
}
```

重载 Caddy：

```bash
caddy reload --config /etc/caddy/Caddyfile
```

## GitHub Actions Pages 配置

仓库已经有前端 Pages workflow：

```text
.github/workflows/deploy-frontend-pages.yml
```

### 当前 workflow 主要改进（2026 年更新）

- 使用 `actions/configure-pages@v4` 初始化 Pages 环境（官方推荐）
- 自动生成 `.nojekyll` 文件，避免 GitHub Pages 的 Jekyll 处理导致的资源 404 问题
- `VITE_BASE` 使用 `github.event.repository.name`（GitHub Actions 表达式支持的稳定写法）
- 构建时优先读取仓库 Variables 中的 `VITE_API_BASE_URL`

### 配置步骤

1. 进入仓库 `Settings -> Secrets and variables -> Actions -> Variables`，新增仓库变量：

   **变量名**：`VITE_API_BASE_URL`
   
   **变量值**：`https://你的真实API域名`（例如 `https://api.xiaoqianran.xyz`）

   > **重要**：不配置时会回退到示例域名，可能导致前端无法与你的后端交互（出现“系统内部错误”等）。

2. 进入 `Settings -> Pages`，将 `Build and deployment` 的 `Source` 设置为 **GitHub Actions**。

3. 推送到 `main` 分支或手动触发 `Deploy Frontend to GitHub Pages` workflow 即可自动部署。

workflow 现在更贴近 GitHub 官方 Pages 部署最佳实践，部署成功率和稳定性显著提升。

## GitHub Pages 路径说明

当前 `deploy-frontend-pages.yml` workflow 使用以下表达式（GitHub Actions 官方支持的写法）：

```yaml
VITE_BASE: /${{ github.event.repository.name }}/
```

这适合 GitHub Pages 默认项目地址，兼容 push 和手动触发：

```text
https://<你的GitHub用户名>.github.io/Java006-CampusHub/
```

如果你给 Pages 绑定了自定义域名，并且网站在域名根路径访问，例如：

```text
https://shiqian.xiaoqianran.xyz/
```

可以手动把 workflow 里的 `VITE_BASE` 改成：

```yaml
VITE_BASE: /
```

否则静态资源路径会多一层仓库名。

**注意**：workflow 内部已自动处理，无需在大多数场景下手动修改。注意 GitHub Actions 表达式语法有限，不支持 `.split()` 等 JS 方法。

## 前端如何指定后端地址

本项目前端支持两种后端地址配置。

### 方式一：GitHub Actions 变量，推荐用于 Pages

在 GitHub Actions Variables 中设置：

```text
VITE_API_BASE_URL=https://api.xiaoqianran.xyz
```

然后重新运行 `Deploy Frontend to GitHub Pages` workflow。

### 方式二：运行时 config.js

本地或自托管静态文件时，可以编辑：

```text
shiqian-frontend/public/config.js
```

或构建后的：

```text
shiqian-frontend/dist/config.js
```

示例：

```js
window.__SHIQIAN_CONFIG__ = {
  apiBaseUrl: 'https://api.xiaoqianran.xyz'
}
```

注意：GitHub Pages 上的 `config.js` 来自仓库构建产物，不能直接登录服务器修改；Pages 场景优先使用 GitHub Actions Variables。

## 前端本地开发代理

本地开发默认代理到：

```text
http://localhost:8080
```

如果本地前端要连服务器后端：

```bash
cd shiqian-frontend
VITE_API_PROXY_TARGET=https://api.xiaoqianran.xyz npm run dev -- --host 0.0.0.0
```

本地开发时，浏览器请求 `/api/...`，Vite 开发服务器会把请求代理到 `VITE_API_PROXY_TARGET`。

## 部署检查清单

1. 后端服务器启动 `shiqian-gateway`，确认监听 `8080`。
2. DNS 添加 `api.xiaoqianran.xyz`，指向你的服务器公网 IP。
3. Caddy 使用上面的 `api.xiaoqianran.xyz` 配置（正确设置 CORS）。
4. Caddy 的 `Access-Control-Allow-Origin` 填你的 GitHub Pages 前端域名（或使用 `*` 临时测试）。
5. **GitHub 仓库设置**：
   - `Settings -> Pages` → Source 选择 **GitHub Actions**
   - `Settings -> Secrets and variables -> Actions -> Variables` 新增 `VITE_API_BASE_URL`（**强烈建议配置**）
6. 推送代码到 `main` 或手动触发 `Deploy Frontend to GitHub Pages` workflow。
7. 部署成功后，在浏览器开发者工具 Network 面板确认接口请求正确发往你的后端域名。

## 本项目已改动的前端文件

| 文件 | 作用 |
|------|------|
| `shiqian-frontend/public/config.js` | 运行时后端地址配置，Pages 场景通常保持空值并使用 GitHub Actions Variables |
| `shiqian-frontend/index.html` | 加载 `/config.js` |
| `shiqian-frontend/src/api/client.ts` | 优先读取运行时配置，其次读取 `VITE_API_BASE_URL` |
| `shiqian-frontend/src/env.d.ts` | 声明 `window.__SHIQIAN_CONFIG__` 类型 |
| `shiqian-frontend/vite.config.ts` | 开发代理支持 `VITE_API_PROXY_TARGET` |
