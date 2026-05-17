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
- 资源广场：`/plaza`
- 分类浏览：`/categories`
- 发布资源：`/publish`
- 我的收藏：`/favorites`
- 我的发布：`/mine`
- 资源详情：`/detail/:id`
- 登录：`/login`
- 注册：`/register`

### 管理端

- 后台首页：`/admin`
- 资源审核：`/audit`
- 资源管理：`/resource-admin`
- 分类管理：`/category-admin`
- 用户管理：`/user-admin`

## 逻辑说明

- 分类浏览点击分类后进入资源广场，并自动设置筛选条件。
- 发布资源后进入待审核状态，并出现在“我的发布”和后台“资源审核”。
- 后台审核通过后，资源进入资源广场。
- 收藏状态由 Pinia 统一维护，广场、详情页、我的收藏状态一致。
