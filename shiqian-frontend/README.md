# 时迁校园内容社区 - Vue3 版

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
- 内容广场：`/explore`
- 博客帖：`/blog`
- 图片专区：`/images`
- 资料分享：`/share`
- 发布内容：`/publish`
- 我的收藏：`/favorites`
- 我的发布：`/mine`
- 个人资料：`/profile`
- 内容详情：`/detail/:id`
- 编辑内容：`/resource/:id/edit`
- 登录：`/login`
- 注册：`/register`

### 管理端

- 后台首页：`/admin`
- 内容审核：`/admin/audit`
- 内容管理：`/admin/resources`
- 回收站：`/admin/recycle-bin`
- 辅助分类管理（兼容旧数据）：`/admin/categories`
- 用户管理：`/admin/users`
- 操作日志：`/admin/logs`

## 逻辑说明

- 首页和导航按“博客 / 图片 / 资料”三个频道组织内容，内容广场用于跨频道搜索。
- 发布时只需填写标题，并在正文、图片、附件中至少提供一种内容；分类和自由标签均为可选项。
- 三个频道都支持正文和多附件，频道用于表达内容形态，而不是限制上传能力。
- 附件选中后立即上传：2MB以内最多4个并发，2–10MB最多2个并发，超过10MB逐个上传；提供真实进度、取消和一次自动重试。
- Markdown 正文采用左右分栏实时预览；内容详情和审核页支持 PDF、Markdown、TXT/常见源码、图片及 ZIP 目录预览。
- 文本预览最多读取前512KB，ZIP目录最多显示500项；RAR、7Z和Office文件保留下载查看。
- 每条内容最多10个附件，单文件不超过50MB。
- 发布后进入待审核状态，并出现在“我的发布”和后台“内容审核”。
- 后台审核通过后，内容进入对应频道和内容广场。
- 收藏状态由 Pinia 统一维护，频道页、详情页、我的收藏状态一致。
