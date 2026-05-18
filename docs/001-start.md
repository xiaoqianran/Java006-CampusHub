~~• 按新环境从零跑，命令顺序如下。

1. 启动 Docker 依赖

docker compose up -d

等 MySQL/Redis/Nacos/ES/RabbitMQ 都起来：

docker compose ps

2. 执行 SQL

如果是全新环境、MySQL volume 也是新的，其实不用手动执行，docker-compose.yml 已经挂载了：

./docker/mysql/init:/docker-entrypoint-initdb.d

MySQL 首次初始化会自动执行：

docker/mysql/init/init.sql
docker/mysql/init/z-demo-data.sql

如果你不是全新 volume，或者想手动重刷 SQL，执行：

docker exec -i shiqian-mysql mysql -uroot -proot < docker/mysql/init/init.sql
docker exec -i shiqian-mysql mysql -uroot -proot < docker/mysql/init/z-demo-data.sql

3. 构建后端 jar

mvn clean package -DskipTests

4. 启动后端

推荐直接用已有脚本：

./restart-backend.sh

它会启动：

Gateway:  http://localhost:8080
User:     http://localhost:8081
Resource: http://localhost:8082

查看日志：

tail -f logs/fresh-user.log
tail -f logs/fresh-resource.log
tail -f logs/fresh-gateway.log

5. 启动前端

cd shiqian-frontend
npm install
npm run dev

前端默认：

http://localhost:5173

前端已经配置 /api 代理到：

http://localhost:8080

所以正常访问前端页面即可走 Gateway。~~