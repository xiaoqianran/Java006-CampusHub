#!/usr/bin/env bash
set -e

echo "=== 重新启动 CampusHub 后端服务 ==="
date

mkdir -p logs

JAVA_OPTS_COMMON="${JAVA_OPTS_COMMON:--XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -Dfile.encoding=UTF-8}"
USER_JAVA_OPTS="${USER_JAVA_OPTS:--Xms128m -Xmx448m}"
RESOURCE_JAVA_OPTS="${RESOURCE_JAVA_OPTS:--Xms128m -Xmx512m}"
GATEWAY_JAVA_OPTS="${GATEWAY_JAVA_OPTS:--Xms128m -Xmx384m}"

# 清理旧进程
echo "→ 清理旧的后端进程..."
pkill -f 'target/shiqian-(user|resource|gateway)' 2>/dev/null || true
sleep 2

echo "→ 启动 shiqian-user (端口 8081)..."
nohup java $JAVA_OPTS_COMMON $USER_JAVA_OPTS \
  -jar shiqian-user/target/shiqian-user-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local \
  > logs/fresh-user.log 2>&1 &
echo $! > logs/user.pid

sleep 5

echo "→ 启动 shiqian-resource (端口 8082)..."
nohup java $JAVA_OPTS_COMMON $RESOURCE_JAVA_OPTS \
  -jar shiqian-resource/target/shiqian-resource-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local \
  > logs/fresh-resource.log 2>&1 &
echo $! > logs/resource.pid

sleep 6

echo "→ 启动 shiqian-gateway (端口 8080)..."
nohup java $JAVA_OPTS_COMMON $GATEWAY_JAVA_OPTS \
  -jar shiqian-gateway/target/shiqian-gateway-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local \
  > logs/fresh-gateway.log 2>&1 &
echo $! > logs/gateway.pid

sleep 4

echo ""
echo "=== 当前后端进程状态 ==="
ps aux | grep -E 'java.*target/shiqian' | grep -v grep | awk '{print "PID:", $2, "服务:", $NF}' || echo "未检测到进程"

echo ""
echo "日志文件："
ls -lh logs/fresh-*.log 2>/dev/null || true

echo ""
echo "✅ 后端服务启动完成"
echo "   - Gateway:   http://localhost:8080"
echo "   - User:      http://localhost:8081"
echo "   - Resource:  http://localhost:8082"
echo ""
echo "提示：使用 tail -f logs/fresh-*.log 查看启动日志"
