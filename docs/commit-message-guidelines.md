# Commit Message Guidelines

本仓库提供 Git 提交信息格式的规范和示例，旨在提高提交信息的可读性和项目的可维护性。

## 常见的提交关键词

- **`feat`**：新功能
  ```plaintext
  feat: 添加用户登录功能
  ```
- **`fix`**：修复 bug
  ```plaintext
  fix: 修复登录页面的错误提示
  ```
- **`docs`**：文档变更
  ```plaintext
  docs: 更新 API 文档
  ```
- **`style`**：代码格式（不影响代码运行的变动）
  ```plaintext
  style: 调整代码格式，删除多余空格
  ```
- **`refactor`**：重构（即不是新增功能，也不是修复 bug 的代码变动）
  ```plaintext
  refactor: 重命名变量以提高代码可读性
  ```
- **`test`**：增加测试
  ```plaintext
  test: 添加用户登录功能的单元测试
  ```
- **`chore`**：构建过程或辅助工具的变动
  ```plaintext
  chore: 更新依赖包版本
  ```
- **`ci`**：持续集成相关配置
  ```plaintext
  ci: 修改 CI 配置文件以支持新的测试框架
  ```
- **`build`**：影响构建系统或外部依赖的更改
  ```plaintext
  build: 修改 webpack 配置文件以优化打包过程
  ```
- **`revert`**：回滚某个更早之前的提交
  ```plaintext
  revert: 回滚提交 1234567
  ```

## 提交信息格式

```plaintext
<type>: <subject>
<body>
```

- **type**：提交类型（如 feat, fix 等）
- **subject**：简要描述变更
- **body**：详细描述变更（可选）

## 示例

1. **性能优化相关的更改**
   ```plaintext
   perf: 优化数据库查询性能
   通过添加索引，减少了查询时间。
   ```

2. **配置文件的修改**
   ```plaintext
   chore: 更新 ESLint 配置
   调整了 ESLint 规则以适应新的代码风格要求。
   ```

3. **代码注释的修改**
   ```plaintext
   docs: 更新函数的注释
   ```

4. **变量名的修改**
   ```plaintext
   refactor: 重命名变量以提高代码可读性
   ```

5. **改变函数的外部行为**
   - 如果是新功能或增强功能：
     ```plaintext
     feat: 修改函数行为以支持新的输入参数
     ```
   - 如果是修复 bug：
     ```plaintext
     fix: 修改函数行为以修复边界条件处理错误
     ```

6. **为 Selenium 驱动的 Chrome 添加设置分辨率的参数**

   ```plaintext
   feat: 为 Selenium 驱动的 Chrome 添加设置分辨率的参数
   通过在启动参数中添加选项，支持在启动 Chrome 时设置窗口分辨率。
   ```

7. **更新项目依赖包**

   ```plaintext
   chore: 更新项目依赖包
   ```

8. **重命名函数以提高代码可读性**

   ```
   refactor: 重命名函数以提高代码可读性
   将 `calculate()` 函数重命名为 `calculateTotalPrice()` 以更好地反映其功能。
   ```

9. **提取重复代码到一个独立的函数**

   ```
   refactor: 提取重复代码到一个独立的函数
   将多处重复的数据库查询逻辑提取到 `queryDatabase()` 函数中。
   ```

10. **重构类结构以实现更好的封装**

    ```
    refactor: 重构类结构以实现更好的封装
    将类 `User` 中的部分方法移到一个新的类 `UserHelper` 中，以提高代码的模块化和可维护性。
    ```

11. **将长函数拆分为多个小函数**

    ```
    refactor: 将长函数拆分为多个小函数
    将 `processOrder()` 函数拆分为 `validateOrder()`, `calculateTotal()`, 和 `saveOrder()` 函数，以提高代码的可读性和复用性。
    ```

12. **移除未使用的代码**

    ```
    refactor: 移除未使用的代码
    移除了 `oldFeature()` 函数及其相关调用，因为该功能已被新的实现取代。
    ```

13. **重构条件判断逻辑**

    ```
    refactor: 重构条件判断逻辑
    将 `processData()` 函数中的复杂嵌套条件重构为多个简单的条件判断，以提高代码的可读性。
    ```

14. **docs**（文档变更）

    ```
    docs: 更新 README 文件
    添加了项目的安装和配置步骤，并修正了一些拼写错误。
    ```

15. **chore**（构建过程或辅助工具的变动）

    ```
    chore: 更新依赖包版本
    将项目中的依赖包版本更新到最新的稳定版本，以确保项目使用最新的库和修复已知的漏洞。
    ```

16. **build**（影响构建系统或外部依赖的更改）

    ```
    build: 修改 webpack 配置
    添加了新的插件以优化打包过程，并调整了输出目录结构。
    ```

---

## 使用说明

- 开发过程中**每个功能完成后**，必须按本规范撰写 commit message。
- 提交前请使用 `git commit`（或通过 IDE 的 commit 功能），确保遵循 `<type>: <subject>` 格式。
- 建议为复杂变更提供 body 部分，简要说明变更原因和影响。
