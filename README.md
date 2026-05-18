# 时迁 - 校园资源共享平台

> 阶段一：项目骨架搭建与基础能力建设

## 项目简介

时迁是一个面向校园场景的资源共享平台，基于 Spring Cloud 微服务架构构建，旨在为校园内的资源发布、共享、流转提供高效便捷的服务支撑。

## 阶段一完成内容

### 1. 多模块工程搭建

```
shiqian-platform（父工程）
├── shiqian-common    公共模块
└── shiqian-user      用户服务
```

| 模块 | 说明 |
|------|------|
| shiqian-common | 统一响应体、全局异常处理、通用工具类 |
| shiqian-user | 用户注册、登录、鉴权、信息管理 |

### 2. 公共能力（shiqian-common）

- **统一响应体** — `Result<T>` 封装 code / message / data，提供 `ok()` / `fail()` 工厂方法
- **响应码枚举** — `ResultCode` 定义 SUCCESS / FAIL / UNAUTHORIZED / FORBIDDEN / NOT_FOUND / PARAM_ERROR
- **业务异常** — `BusinessException` 支持 message / code+message / ResultCode 三种构造
- **全局异常处理** — `GlobalExceptionHandler` 覆盖 5 类异常 + 兜底处理

| 异常类型 | HTTP 状态码 | 处理方式 |
|---------|------------|---------|
| BusinessException | 200 | 返回业务错误码与消息 |
| MethodArgumentNotValidException | 400 | 提取首个校验错误 |
| BindException | 400 | 提取首个绑定错误 |
| ConstraintViolationException | 400 | 提取首个约束违规 |
| HttpRequestMethodNotSupportedException | 405 | 返回不支持请求方法提示 |
| 其他 Exception | 500 | 返回系统内部错误（不暴露堆栈） |

### 3. 用户服务（shiqian-user）

- **启动类** — `ShIQianUserApplication`，启用 Nacos 服务发现
- **实体层** — `User` 实体，MyBatis-Plus 注解、自动填充（createTime / updateTime）、逻辑删除
- **数据层** — `UserMapper` 继承 `BaseMapper<User>`
- **服务层** — `UserService` / `UserServiceImpl`，含数据库连接检测
- **控制层** — `UserController`，健康检查端点 `GET /api/user/health`
- **配置层** — Security 放行、MyBatis-Plus 自动填充与分页插件

### 4. CI/CD 流水线

通过 `.cnb.yml` 配置了 CNB 云原生构建流水线：

- 所有分支的 **push** 和 **pull_request** 事件自动触发
- 构建环境：Maven 3.9 + JDK 17
- 执行 `mvn test -B` 进行编译与测试验证
- `.m2` 仓库缓存加速依赖下载

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 3.2.0 |
| 微服务 | Spring Cloud | 2023.0.0 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.2 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 8.0.33 |
| 服务发现/配置 | Nacos | - |
| 缓存 | Spring Data Redis | - |
| 消息队列 | Spring AMQP (RabbitMQ) | - |
| API 文档 | Knife4j (OpenAPI 3) | 4.3.0 |
| 工具库 | Hutool | 5.8.25 |
| 构建工具 | Maven | - |
| JDK | Eclipse Temurin | 17 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Nacos 2.x（可选，local 环境默认关闭）

### 构建

```bash
mvn clean package -DskipTests
```

### 本地运行

1. 创建数据库 `shiqian_user`，执行建表语句
2. 启动 Nacos（或使用 local 配置跳过）
3. 运行用户服务：

```bash
cd shiqian-user
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

4. 访问健康检查：`http://localhost:8081/api/user/health`

## 后续规划

- [ ] 用户注册与登录接口
- [ ] JWT 鉴权与权限拦截
- [ ] 单元测试与集成测试覆盖
- [ ] 资源服务模块（shiqian-resource）
- [ ] 网关服务（shiqian-gateway）
- [ ] 消息通知模块

## 完整本地开发环境启动（当前架构）

> **注意**：分类名称乱码问题（mojibake）通常由 SQL 导入时客户端字符集错误导致，而非前端问题。前端静态中文正常，动态 `category.name` 来自数据库。

### 1. 一键启动基础设施（推荐）

```bash
docker compose up -d mysql redis
```

MySQL 已通过 `docker/mysql/init/*.sql` 自动初始化（含 utf8mb4 + 分类种子数据）。

### 2. 手动导入 SQL 时必须强制 UTF-8（防止乱码）

如果手动执行 SQL 或修复已有乱码数据：

```bash
# 1. 删除旧库（可选，谨慎操作）
docker exec -i shiqian-mysql mysql -uroot -proot --default-character-set=utf8mb4 \
  -e "DROP DATABASE IF EXISTS shiqian_user; DROP DATABASE IF EXISTS shiqian_resource;"

# 2. 使用 --default-character-set=utf8mb4 重新导入
docker exec -i shiqian-mysql sh -c 'mysql -uroot -proot --default-character-set=utf8mb4' < docker/mysql/init/init.sql
docker exec -i shiqian-mysql sh -c 'mysql -uroot -proot --default-character-set=utf8mb4' < docker/mysql/init/z-demo-data.sql

# 或者直接在 MySQL 客户端内执行：
#   SET NAMES utf8mb4;
#   SOURCE /path/to/z-demo-data.sql;
```

**验证中文是否正常**：
```sql
USE shiqian_resource;
SELECT id, name, HEX(name) FROM t_category LIMIT 3;
-- 正确时 name 显示“计算机科学”，HEX 以 E4 B8 AD ... 开头
```

### 3. 修复分类乱码后的必要操作（Redis 缓存）

分类树使用了 Spring `@Cacheable("category:tree")` 缓存（见 `CategoryServiceImpl.java`）。

修复数据库数据后，**必须清理缓存**：

```bash
# 方案 A：重启 Redis
docker restart shiqian-redis

# 方案 B：手动删除缓存键（进入 redis-cli）
docker exec -it shiqian-redis redis-cli DEL "category:tree"

# 方案 C：重启 resource 服务
```

### 4. 启动后端服务

```bash
mvn clean package -DskipTests -pl shiqian-common,shiqian-user,shiqian-resource,shiqian-gateway -am

# 分别启动（local profile）
java -jar shiqian-user/target/shiqian-user-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar shiqian-resource/target/shiqian-resource-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar shiqian-gateway/target/shiqian-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

访问：
- Gateway: http://localhost:8080
- Resource 健康：http://localhost:8082/api/category/tree （应返回正常中文分类）
- 用户服务：http://localhost:8081

### 5. 启动前端

```bash
cd shiqian-frontend
npm install
npm run dev
```

---

**根本原因总结**：
- JDBC URL 已统一加强为 `characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci`
- 数据库/表 DDL 均为 `utf8mb4_unicode_ci`
- 问题多发生于“旧数据”或“错误编码的 SQL 客户端导入”
- 前端从不修改 `category.name`，仅原样渲染后端返回的值
