---
name: vue-official
description: Vue 技术栈官方资源搜索技能。在回答 Vue 3、Vue Router、Element Plus 组件、Vite、TypeScript 相关问题前，必须先使用 web_fetch 拉取对应官方 llms.txt / 文档获取最新信息。特别强制在推荐或使用任何 Element Plus 组件前搜索。适用于 shiqian-frontend 项目。
---

# Vue Official Resources (Search-First Policy)

**核心强制规则：Search First — 每次使用对应技术前必须先搜索官方最新文档**

在提供任何 Vue 技术栈的代码、组件推荐、API 用法、配置建议**之前**，**必须**先调用 `web_fetch` 工具获取官方 `llms.txt`（或 `llms-full.txt`），基于实时官方内容再进行回答。严禁依赖过时记忆直接输出。

## 官方资源清单（直接可用）

### Vue 3（核心）
- 推荐（轻量、快速）：`https://vuejs.org/llms.txt`
- 完整版（详细 API）：`https://vuejs.org/llms-full.txt`

### Vue Router 4
- 推荐：`https://router.vuejs.org/llms.txt`
- 完整版：`https://router.vuejs.org/llms-full.txt`

### Element Plus（组件库 — 最重要）
- 推荐（组件 API 首选）：`https://element-plus.org/llms.txt`
- 完整版：`https://element-plus.org/llms-full.txt`

### Vite（构建工具）
- 推荐：`https://vite.dev/llms.txt`
- 完整版：`https://vite.dev/llms-full.txt`

### TypeScript 官方文档
- 主入口：`https://www.typescriptlang.org/docs/`
- 常用 Handbook 页面示例：
  - Everyday Types: `https://www.typescriptlang.org/docs/handbook/2/everyday-types.html`
  - More on Functions: `https://www.typescriptlang.org/docs/handbook/2/functions.html`
  - Classes / Interfaces / Generics 等按需补充路径

### Vue AI Skills（优秀实践集合）
- GitHub: `https://github.com/vuejs-ai/skills`
- 需要时可 fetch 其 README 或具体 skill 文件的 raw 内容

## 强制执行流程

1. **识别用户意图**  
   分析问题涉及哪个技术域：
   - Vue 3 Composition API / SFC / 响应式 / 生命周期 → Vue 官方
   - 路由、导航守卫、动态路由 → Vue Router
   - 任何 `el-` 组件（el-table、el-form、el-dialog、el-select、el-date-picker 等）→ **Element Plus（必须优先）**
   - `vite.config.ts`、插件、alias、构建优化 → Vite
   - 类型定义、泛型、Vue + TS 集成 → TypeScript 文档

2. **执行搜索（必须先做）**  
   使用 `web_fetch` 工具拉取对应 URL：
   ```bash
   # 示例：想用 Element Plus 的 el-table 前
   web_fetch https://element-plus.org/llms.txt
   ```
   - 普通问题优先用 `llms.txt`（体积小、针对 LLM 优化）
   - 需要完整 props、events、slots、方法时使用 `llms-full.txt`

3. **基于官方内容回答**  
   读取工具返回的最新 Markdown 内容后，再给出代码、解释或推荐。

4. **必要时多次搜索**  
   一个问题可能需要同时 fetch Vue + Element Plus + Vite。

## 典型必须搜索的场景（高频）

- 推荐或实现 Element Plus 任意组件
- 使用 `useRoute`、`useRouter`、`RouterLink`、导航守卫
- `<script setup>` + `defineProps`、`defineEmits` + TypeScript 类型
- Vite 插件配置（`@vitejs/plugin-vue` 等）
- Pinia store 与 Vue 3 + TS 结合（虽未列出官方 llms，但可参考 Vue 官方模式）
- 性能优化、异步组件、Suspense 等 Vue 3 高级特性

## 最佳实践提示

- **Element Plus 是重点**：其组件 API 更新较快，`llms.txt` 是目前最准确的来源，**坚决不要凭记忆写 el- 组件的 props**。
- 一次对话中，重要操作前可重复 fetch（文档可能更新）。
- 如果 `web_fetch` 返回内容过长，可先 fetch `llms.txt`，再根据需要 fetch 具体子页面。
- 回答时可简要说明 “已参考官方最新 llms.txt” 增加可信度。

## 工具使用示例

```text
用户：帮我实现一个带分页和多选的 el-table

AI 思考：
1. 涉及 Element Plus 的 el-table → 必须先搜索
2. 调用：
   web_fetch url="https://element-plus.org/llms.txt"
   （或 llms-full.txt）
3. 读取 Table 组件的最新 columns、data、selection、pagination 用法
4. 再给出正确代码
```

**记住：Search First 是这条技能的最高优先级规则，违反即为错误回答。**

---

此技能专为 CampusHub 项目 `shiqian-frontend`（Vue 3 + Element Plus + Vite + TypeScript + Vue Router）量身设计。
