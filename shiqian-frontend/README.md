# 时迁校园资源共享平台 - Vue3 重写版

这是从原 `index.html` 整合原型改写而来的 Vue3 工程版本。

## 技术栈

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Element Plus

## 运行

```bash
npm install
npm run dev
```

## 构建

```bash
npm run build
```

## 页面结构

### 学生端

- 首页：`/home`
- 资源中心（搜索、分类、排序统一入口）：`/resources`
- 发布资源：`/publish`
- 我的收藏：`/favorites`
- 我的发布：`/mine`
- 个人资料：`/profile`
- 资源详情：`/detail/:id`
- 编辑资源：`/resource/:id/edit`
- 登录：`/login`
- 注册：`/register`

### 管理端

- 后台首页：`/admin`
- 资源审核：`/admin/audit`
- 资源管理：`/admin/resources`
- 回收站：`/admin/recycle-bin`
- 分类管理：`/admin/categories`
- 用户管理：`/admin/users`
- 操作日志：`/admin/logs`

## 逻辑说明

- 首页搜索与分类快捷入口统一进入资源中心，筛选条件保存在 URL 中。
- 发布资源后进入待审核状态，并出现在“我的发布”和后台“资源审核”。
- 后台审核通过后，资源进入资源中心。
- 收藏状态由 Pinia 统一维护，资源中心、详情页、我的收藏状态一致。
