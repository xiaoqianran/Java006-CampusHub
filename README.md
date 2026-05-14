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
