#!/usr/bin/env bash
set -Eeuo pipefail

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

: "${MYSQL_ROOT_PASSWORD:?请先复制 .env.example 为 .env 并设置 MYSQL_ROOT_PASSWORD}"
: "${JWT_SECRET:?请在 .env 中设置 JWT_SECRET}"
: "${REDIS_PASSWORD:?请在 .env 中设置 REDIS_PASSWORD}"
: "${RABBITMQ_USER:?请在 .env 中设置 RABBITMQ_USER}"
: "${RABBITMQ_PASSWORD:?请在 .env 中设置 RABBITMQ_PASSWORD}"

echo "=== 重新启动 CampusHub 后端服务 ==="
date

mkdir -p logs

wait_service() {
  local name="$1"
  local port="$2"
  local pid_file="$3"
  local log_file="$4"
  local timeout_seconds="${5:-120}"
  local pid=""
  local waited=0

  pid="$(cat "$pid_file")"
  while (( waited < timeout_seconds )); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "✗ $name 启动失败：进程已退出" >&2
      tail -60 "$log_file" >&2 || true
      return 1
    fi

    if timeout 2 bash -c "cat < /dev/null > /dev/tcp/127.0.0.1/$port" 2>/dev/null; then
      echo "✓ $name 已就绪（端口 $port）"
      return 0
    fi

    sleep 2
    (( waited += 2 ))
  done

  echo "✗ $name 在 ${timeout_seconds}s 内未监听端口 $port" >&2
  tail -60 "$log_file" >&2 || true
  return 1
}

start_service() {
  local name="$1"
  local pid_file="$2"
  local log_file="$3"
  shift 3

  # setsid 让应用脱离当前终端会话，避免启动脚本退出时服务被一并回收。
  setsid "$@" </dev/null > "$log_file" 2>&1 &
  local pid=$!
  echo "$pid" > "$pid_file"
  echo "  PID: $pid"
}

# 轻量级数据库就绪检查（推荐使用 ./02_StartBackend.sh 获得完整自动修复能力）
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q '^shiqian-mysql$'; then
  echo "→ 检查 MySQL 数据库状态..."
  user_table_count="$(docker exec shiqian-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse \
    "SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = 'shiqian_user' AND TABLE_NAME = 't_user';" \
    2>/dev/null || echo "0")"
  if [[ "$user_table_count" != "1" ]]; then
    echo "   [警告] shiqian_user 数据库可能未就绪"
    echo "   建议执行：./02_StartBackend.sh （会自动修复数据库）"
    echo "   或手动创建：docker/mysql/init/ 下的 SQL 已包含所需结构"
  else
    echo "   ✓ shiqian_user 数据库检测正常"
  fi
fi

JAVA_OPTS_COMMON="${JAVA_OPTS_COMMON:--XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -Dfile.encoding=UTF-8}"
USER_JAVA_OPTS="${USER_JAVA_OPTS:--Xms128m -Xmx448m}"
RESOURCE_JAVA_OPTS="${RESOURCE_JAVA_OPTS:--Xms128m -Xmx512m}"
GATEWAY_JAVA_OPTS="${GATEWAY_JAVA_OPTS:--Xms128m -Xmx384m}"

# 清理旧进程
echo "→ 清理旧的后端进程..."
pkill -f 'target/shiqian-(user|resource|gateway)' 2>/dev/null || true
sleep 2

echo "→ 启动 shiqian-user (端口 8081)..."
start_service "shiqian-user" "logs/user.pid" "logs/fresh-user.log" \
  java $JAVA_OPTS_COMMON $USER_JAVA_OPTS \
  -jar shiqian-user/target/shiqian-user-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local
wait_service "shiqian-user" 8081 "logs/user.pid" "logs/fresh-user.log"

echo "→ 启动 shiqian-resource (端口 8082)..."
start_service "shiqian-resource" "logs/resource.pid" "logs/fresh-resource.log" \
  java $JAVA_OPTS_COMMON $RESOURCE_JAVA_OPTS \
  -jar shiqian-resource/target/shiqian-resource-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local
wait_service "shiqian-resource" 8082 "logs/resource.pid" "logs/fresh-resource.log"

echo "→ 启动 shiqian-gateway (端口 8080)..."
start_service "shiqian-gateway" "logs/gateway.pid" "logs/fresh-gateway.log" \
  java $JAVA_OPTS_COMMON $GATEWAY_JAVA_OPTS \
  -jar shiqian-gateway/target/shiqian-gateway-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local
wait_service "shiqian-gateway" 8080 "logs/gateway.pid" "logs/fresh-gateway.log"

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
