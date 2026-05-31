# GitHub Actions 与部署说明

## 当前已支持

本仓库已添加两个 GitHub Actions workflow：

- `.github/workflows/ci.yml`：后端 Maven 校验、测试、打包；前端 `npm ci` 与 `npm run build`。
- `.github/workflows/deploy-frontend-pages.yml`：把 `shiqian-frontend` 构建产物部署到 GitHub Pages。

## GitHub 能不能部署整个项目

GitHub Pages 只能托管静态文件，所以它可以部署本项目的 Vue 前端，不能直接运行 Spring Boot 后端、MySQL、Redis、RabbitMQ、Nacos、Elasticsearch 等服务。

完整项目部署需要额外运行环境，例如：

- 云服务器 / VPS：用 GitHub Actions 打包后，通过 SSH 上传 jar 或 Docker 镜像并重启服务。
- 容器平台：把后端服务构建成镜像，推送到 GHCR/Docker Hub，再由服务器或平台拉取运行。
- 前后端分离：前端放 GitHub Pages，后端放云服务器、Render、Railway、Fly.io、阿里云、腾讯云等。

## 启用 GitHub Pages

1. 推送代码到 GitHub。
2. 进入仓库 `Settings -> Pages`。
3. `Build and deployment` 的 `Source` 选择 `GitHub Actions`。
4. 推送到 `main` 分支，或手动运行 `Deploy Frontend to GitHub Pages`。

部署完成后，页面地址通常是：

```text
https://<你的用户名>.github.io/<仓库名>/
```

## 配置 GitHub Pages 前端访问服务器后端

本项目推荐前后端分离：

- 前端部署到 GitHub Pages
- 后端部署到你的服务器
- 服务器用 Caddy 提供 `https://api.xiaoqianran.xyz`

进入仓库 `Settings -> Secrets and variables -> Actions -> Variables`，新增：

```text
VITE_API_BASE_URL=https://api.xiaoqianran.xyz
```

不配置时，前端会默认请求当前 Pages 域名下的 `/api/...`，这不适合 GitHub Pages + 独立服务器后端。

完整 Caddyfile、CORS 和 Pages 配置见：

[github-pages-caddy-api.md](github-pages-caddy-api.md)

## CI 产物

`CI` workflow 会上传：

- `backend-jars`：后端三个服务的 jar 包。
- `frontend-dist`：前端静态构建产物。

这些产物能用于下载验证，但不等于后端已经在线运行。
