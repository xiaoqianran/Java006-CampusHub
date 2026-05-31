# CampusHub 线上前后端联调测试命令

本文档用于验证线上后端 `https://api.xiaoqianran.xyz`、GitHub Pages 前端 `https://xiaoqianran.github.io/Java006-CampusHub`，以及两者之间的 CORS 配置是否正确。

## 结论摘要

当前实测结果：

- 后端健康检查接口可访问。
- 后端数据库连接正常。
- 资源分类公开接口可访问。
- GitHub Pages 前端页面可访问。
- 主要风险点是 CORS：浏览器请求的 `Origin` 是 `https://xiaoqianran.github.io`，不是 `https://xiaoqianran.github.io/Java006-CampusHub`。

后端 CORS 的允许源应该配置为：

```text
https://xiaoqianran.github.io
```

不要配置为：

```text
https://xiaoqianran.github.io/Java006-CampusHub
```

## 1. 后端健康检查

```bash
curl -i https://api.xiaoqianran.xyz/api/user/health
```

期望结果：

```text
HTTP/2 200
```

响应体应包含类似内容：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "database": "CONNECTED",
    "service": "shiqian-user",
    "status": "UP"
  },
  "success": true
}
```

如果 `database` 是 `CONNECTED`，说明用户服务和数据库连接正常。

## 2. 后端公开接口测试

### 分类树

```bash
curl -i https://api.xiaoqianran.xyz/api/category/tree
```

期望结果：

```text
HTTP/2 200
```

响应体中 `data` 应为分类数组。

### 资源分页

```bash
curl -i 'https://api.xiaoqianran.xyz/api/resource?page=1&size=10'
```

期望结果：

```text
HTTP/2 200
```

响应体中 `data.records` 应为资源列表。

## 3. CORS 预检测试

这是排查 GitHub Pages 前端无法请求后端的关键命令。

```bash
curl -i -X OPTIONS 'https://api.xiaoqianran.xyz/api/category/tree' \
  -H 'Origin: https://xiaoqianran.github.io' \
  -H 'Access-Control-Request-Method: GET'
```

正确结果应包含：

```text
HTTP/2 204
access-control-allow-origin: https://xiaoqianran.github.io
access-control-allow-methods: GET,POST,PUT,DELETE,OPTIONS
access-control-allow-headers: Authorization,Content-Type
```

如果返回的是下面这样，浏览器会拦截请求：

```text
access-control-allow-origin: https://xiaoqianran.github.io/Java006-CampusHub
```

原因是浏览器的 `Origin` 只包含协议、域名和端口，不包含路径。

## 4. 模拟前端真实跨域 GET 请求

```bash
curl -i 'https://api.xiaoqianran.xyz/api/category/tree' \
  -H 'Origin: https://xiaoqianran.github.io'
```

正确结果应包含：

```text
HTTP/2 200
access-control-allow-origin: https://xiaoqianran.github.io
```

如果 `access-control-allow-origin` 不是 `https://xiaoqianran.github.io`，即使命令行能拿到 JSON，浏览器前端仍会因为 CORS 失败。

## 5. 用户注册和登录测试

### 注册测试用户

用户名需要唯一。如果提示用户名已存在，换一个用户名即可。

```bash
curl -i -X POST 'https://api.xiaoqianran.xyz/api/user/register' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "test_user_0531",
    "password": "123456",
    "nickname": "测试用户",
    "email": "test0531@example.com",
    "phone": "13800000000"
  }'
```

期望结果：

```text
HTTP/2 200
```

### 登录

```bash
curl -i -X POST 'https://api.xiaoqianran.xyz/api/user/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "test_user_0531",
    "password": "123456"
  }'
```

期望结果：

```text
HTTP/2 200
```

响应体中应包含登录 token。

### 使用 jq 提取 token

如果本机安装了 `jq`，可以执行：

```bash
TOKEN=$(curl -s -X POST 'https://api.xiaoqianran.xyz/api/user/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"test_user_0531","password":"123456"}' \
  | jq -r '.data.accessToken')
```

然后测试登录态接口：

```bash
curl -i 'https://api.xiaoqianran.xyz/api/user/me' \
  -H "Authorization: Bearer $TOKEN"
```

期望结果：

```text
HTTP/2 200
```

## 6. 前端部署检查

### 检查 GitHub Pages 首页

```bash
curl -i https://xiaoqianran.github.io/Java006-CampusHub/
```

期望结果：

```text
HTTP/2 200
```

HTML 中应包含类似资源路径：

```html
<script src="/Java006-CampusHub/config.js"></script>
<script type="module" crossorigin src="/Java006-CampusHub/assets/index-xxx.js"></script>
```

### 检查运行时配置

```bash
curl -i https://xiaoqianran.github.io/Java006-CampusHub/config.js
```

当前线上配置可能类似：

```js
window.__SHIQIAN_CONFIG__ = {
  apiBaseUrl: ''
}
```

如果 `apiBaseUrl` 为空，前端会继续使用构建时写入的 `VITE_API_BASE_URL`。如果构建时没有设置 `VITE_API_BASE_URL`，前端会请求 GitHub Pages 域名下的 `/api`，这会失败。

### 检查构建产物是否包含后端域名

先从首页 HTML 找到实际 JS 文件名，例如：

```text
/Java006-CampusHub/assets/index-CL-C83ub.js
```

再执行：

```bash
curl -s https://xiaoqianran.github.io/Java006-CampusHub/assets/index-CL-C83ub.js \
  | grep -o 'api.xiaoqianran.xyz'
```

如果输出：

```text
api.xiaoqianran.xyz
```

说明前端构建产物已经指向线上后端。

如果没有输出，说明 GitHub Actions 构建时没有正确设置：

```text
VITE_API_BASE_URL=https://api.xiaoqianran.xyz
```

也可以临时把 `public/config.js` 或部署后的 `config.js` 改成：

```js
window.__SHIQIAN_CONFIG__ = {
  apiBaseUrl: 'https://api.xiaoqianran.xyz'
}
```

## 7. 推荐的最终验证顺序

1. 验证后端健康：

```bash
curl -i https://api.xiaoqianran.xyz/api/user/health
```

2. 验证后端公开接口：

```bash
curl -i https://api.xiaoqianran.xyz/api/category/tree
```

3. 验证 CORS：

```bash
curl -i -X OPTIONS 'https://api.xiaoqianran.xyz/api/category/tree' \
  -H 'Origin: https://xiaoqianran.github.io' \
  -H 'Access-Control-Request-Method: GET'
```

4. 验证前端页面：

```bash
curl -i https://xiaoqianran.github.io/Java006-CampusHub/
```

5. 验证前端构建产物是否指向后端：

```bash
curl -s https://xiaoqianran.github.io/Java006-CampusHub/assets/index-CL-C83ub.js \
  | grep -o 'api.xiaoqianran.xyz'
```

## 8. 判定标准

前后端协同成功需要同时满足：

- `https://api.xiaoqianran.xyz/api/user/health` 返回 `200`。
- `https://api.xiaoqianran.xyz/api/category/tree` 返回 `200`。
- CORS 预检返回 `access-control-allow-origin: https://xiaoqianran.github.io`。
- GitHub Pages 前端构建产物包含或运行时配置指定 `https://api.xiaoqianran.xyz`。

只要 CORS 仍返回带路径的源：

```text
https://xiaoqianran.github.io/Java006-CampusHub
```

浏览器前端就仍可能无法请求后端。
