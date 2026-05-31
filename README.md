# CampusHub 校园资源共享平台

CampusHub 是一个面向校园场景的资源共享平台，包含后端 Spring Cloud 微服务、Vue 3 前端，以及本地开发所需的 MySQL、Redis、Nacos、Elasticsearch、RabbitMQ 等基础设施。

当前项目已经整理为脚本化启动方式。日常开发、演示、服务器部署时，优先使用根目录下的 `01` 到 `05` 脚本，不建议再手动一个个执行 Docker、Maven、Java、npm 命令。

## 快速启动

### 1. 首次初始化环境

只在新机器或环境缺依赖时执行一次：

```bash
chmod +x 01_Environment.sh
./01_Environment.sh
```

这个脚本负责安装或配置开发环境，包括 Java、Maven、Node.js、Docker、Docker Compose 等。

### 2. 启动后端

```bash
./02_StartBackend.sh
```

这个脚本会自动完成：

- 启动基础设施容器：MySQL、Redis、Nacos、Elasticsearch、RabbitMQ
- 等待容器就绪
- 使用 Maven 打包后端
- 启动 `shiqian-user`、`shiqian-resource`、`shiqian-gateway`
- 写入后端日志到 `logs/fresh-*.log`

后端地址：

- Gateway: `http://localhost:8080`
- User: `http://localhost:8081`
- Resource: `http://localhost:8082`

### 3. 停止后端

```bash
./03_StopBackend.sh
```

这个脚本会停止后端 Java 进程，并执行 `docker compose down` 停掉基础设施容器。

### 4. 启动前端

```bash
./04_StartFrontend.sh
```

这个脚本会自动完成：

- 进入 `shiqian-frontend`
- 执行 `npm install`
- 后台执行 `npm run dev`
- 写入日志到 `logs/fresh-frontend.log`
- 写入 PID 到 `logs/frontend.pid`

前端地址：

- `http://localhost:5173`

### 5. 停止前端

```bash
./05_StopFrontend.sh
```

这个脚本会优先按 `logs/frontend.pid` 停止前端，并兜底停止 `npm run dev` / Vite 进程。

## 推荐启动顺序

日常完整启动：

```bash
./02_StartBackend.sh
./04_StartFrontend.sh
```

日常完整停止：

```bash
./05_StopFrontend.sh
./03_StopBackend.sh
```

查看日志：

```bash
tail -f logs/fresh-*.log
tail -f logs/fresh-frontend.log
```

## 服务器更新代码

服务器上拉取最新代码：

```bash
git pull origin main
```

如果只想重启 RabbitMQ：

```bash
docker compose rm -sf rabbitmq
docker compose up -d rabbitmq
docker logs shiqian-rabbitmq --tail 200
```

如果要整套重启：

```bash
./05_StopFrontend.sh
./03_StopBackend.sh
./02_StartBackend.sh
./04_StartFrontend.sh
```

## 项目结构

```text
.
├── 01_Environment.sh        # 首次环境初始化
├── 02_StartBackend.sh       # 启动基础设施和后端
├── 03_StopBackend.sh        # 停止后端和 Docker Compose
├── 04_StartFrontend.sh      # 安装依赖并启动前端
├── 05_StopFrontend.sh       # 停止前端
├── restart-backend.sh       # 被 02 调用，单独重启三个后端 Java 服务
├── docker-compose.yml       # 本地基础设施容器
├── docker/                  # MySQL、RabbitMQ、Prometheus、Grafana 配置
├── shiqian-common/          # 后端公共模块
├── shiqian-user/            # 用户服务，端口 8081
├── shiqian-resource/        # 资源服务，端口 8082
├── shiqian-gateway/         # 网关服务，端口 8080
└── shiqian-frontend/        # Vue 3 + Vite 前端
```

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 后端 | Spring Boot, Spring Cloud, Spring Cloud Alibaba |
| 网关 | Spring Cloud Gateway |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| 注册/配置 | Nacos |
| 搜索 | Elasticsearch |
| 消息队列 | RabbitMQ |
| ORM | MyBatis-Plus |
| 前端 | Vue 3, Vite, Pinia, Element Plus |
| 构建 | Maven, npm |
| 容器 | Docker Compose |

## 默认账号

MySQL 初始化脚本会导入演示数据，默认账号如下：

| 用户名 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `123456` | ADMIN | 系统管理员 |
| `student01` | `123456` | USER | 普通学生账号 |

演示账号来源于 `docker/mysql/init/z-demo-data.sql`。生产环境请修改密码或移除演示数据。

## Debug / 排查用手动命令

下面这些命令不是日常推荐启动方式。只有在排查某个服务、看控制台堆栈、单独调试 Maven 或前端时再使用。

### 手动启动基础设施

脚本 `02_StartBackend.sh` 已经会自动执行这一步。手动命令只用于单独排查 Docker 容器：

```bash
docker compose up -d mysql redis nacos elasticsearch rabbitmq
docker compose ps
docker logs shiqian-rabbitmq --tail 200
```

如果需要停掉容器：

```bash
docker compose down
```

### 手动 Maven 打包

脚本 `02_StartBackend.sh` 已经会自动打包。手动打包主要用于定位编译错误：

```bash
mvn clean package -DskipTests
```

如果机器内存较小，可以沿用脚本里的内存限制：

```bash
MAVEN_OPTS="-Xms256m -Xmx1024m -XX:MaxMetaspaceSize=384m" mvn clean package -DskipTests
```

### 手动启动后端服务

脚本 `restart-backend.sh` 已经负责启动三个 Java 服务。下面命令只建议在 Debug 单个服务时使用，例如想直接在当前终端看启动堆栈：

```bash
java -jar shiqian-user/target/shiqian-user-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar shiqian-resource/target/shiqian-resource-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar shiqian-gateway/target/shiqian-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

如果要按脚本同等内存限制启动，可参考：

```bash
java -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -Xms128m -Xmx448m \
  -jar shiqian-user/target/shiqian-user-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local
```

### 手动启动前端

脚本 `04_StartFrontend.sh` 已经会自动执行 `npm install` 和 `npm run dev`。手动方式只用于排查前端依赖、Vite 报错或代理问题：

```bash
cd shiqian-frontend
npm install
npm run dev
```

前端默认代理 `/api` 到 `http://localhost:8080`。如需临时修改后端地址：

```bash
cd shiqian-frontend
VITE_API_PROXY_TARGET=http://localhost:8080 npm run dev
```

## 常见问题

### RabbitMQ 提示 deprecated environment variables

不要在 `docker-compose.yml` 中使用 `RABBITMQ_VM_MEMORY_HIGH_WATERMARK`。当前项目已经改为配置文件：

```text
docker/rabbitmq/rabbitmq.conf
```

内容为：

```text
vm_memory_high_watermark.relative = 0.6
```

如果服务器仍然看到旧环境变量，说明代码没有拉到最新：

```bash
git pull origin main
grep -A 20 -B 5 "rabbitmq:" docker-compose.yml
```

### 中文分类乱码

分类名称乱码通常是 SQL 导入时客户端字符集错误导致，不是前端问题。手动导入 SQL 时使用：

```bash
docker exec -i shiqian-mysql sh -c 'mysql -uroot -proot --default-character-set=utf8mb4' < docker/mysql/init/init.sql
docker exec -i shiqian-mysql sh -c 'mysql -uroot -proot --default-character-set=utf8mb4' < docker/mysql/init/z-demo-data.sql
```

如果修复了数据库里的分类中文，还需要清理 Redis 缓存：

```bash
docker exec -it shiqian-redis redis-cli DEL "category:tree"
```

### 端口占用

常用端口：

| 服务 | 端口 |
| --- | --- |
| Gateway | 8080 |
| User | 8081 |
| Resource | 8082 |
| Frontend | 5173 |
| MySQL | 3306 |
| Redis | 6379 |
| Nacos | 8848 |
| Elasticsearch | 9200 |
| RabbitMQ | 5672 |
| RabbitMQ Management | 15672 |

如果端口占用，先使用停止脚本：

```bash
./05_StopFrontend.sh
./03_StopBackend.sh
```

## 说明

当前 README 以脚本化启动为准。旧的“手动 Maven 启动各服务、手动 npm 启动前端、手动 docker compose 启动依赖”的流程仍保留在 Debug 章节，但它们主要用于排查问题，不作为日常启动方式。
