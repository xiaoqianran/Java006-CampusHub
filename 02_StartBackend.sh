#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

log() {
  echo ""
  echo "=== $* ==="
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

usage() {
  cat <<'EOF'
用法:
  ./02_StartBackend.sh

说明:
  本脚本只启动基础设施容器、打包并启动后端。
  首次使用请先执行：./01_Environment.sh
EOF
}

wait_http() {
  local name="$1"
  local url="$2"

  while true; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name 已就绪"
      return 0
    fi
    echo "等待 $name ..."
    sleep 2
  done
}

wait_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"

  while true; do
    if timeout 2 bash -c "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null; then
      echo "$name 已就绪"
      return 0
    fi
    echo "等待 $name ..."
    sleep 2
  done
}

container_id() {
  docker compose ps -q "$1"
}

wait_container_running() {
  local service="$1"
  local name="$2"
  local cid=""
  local state=""

  while true; do
    cid="$(container_id "$service")"
    if [[ -n "$cid" ]]; then
      state="$(docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || true)"
      if [[ "$state" == "running" ]]; then
        echo "$name 容器已启动"
        return 0
      fi
    fi

    echo "等待 $name 容器启动 ..."
    sleep 2
  done
}

wait_container_healthy() {
  local service="$1"
  local name="$2"
  local cid=""
  local health=""

  wait_container_running "$service" "$name"
  cid="$(container_id "$service")"

  while true; do
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || true)"
    case "$health" in
      healthy)
        echo "$name 已就绪"
        return 0
        ;;
      none)
        echo "$name 未配置 Docker healthcheck，跳过 health 状态等待"
        return 0
        ;;
      unhealthy)
        echo "$name healthcheck 当前为 unhealthy，继续等待 ..."
        ;;
      *)
        echo "等待 $name healthcheck 变为 healthy ..."
        ;;
    esac
    sleep 2
  done
}

# 确保 MySQL 中的 shiqian_user 和 shiqian_resource 数据库结构完整
# 解决因 volume 已存在导致初始化脚本不执行的问题（常见于反复启动）
ensure_mysql_databases() {
  local mysql_container="shiqian-mysql"
  local root_user="root"
  local user_table_count="0"
  local resource_table_count="0"
  # 优先使用环境变量 MYSQL_ROOT_PASSWORD，否则默认 root（与 docker-compose.yml 一致）
  local root_pass="${MYSQL_ROOT_PASSWORD:-root}"

  echo "→ 检查 MySQL 数据库结构完整性..."

  local need_init=false

  # 检查 shiqian_user 数据库及核心表
  user_table_count="$(docker exec "$mysql_container" \
      mysql -u"$root_user" -p"$root_pass" -Nse \
      "SELECT COUNT(*) FROM information_schema.TABLES
       WHERE TABLE_SCHEMA = 'shiqian_user' AND TABLE_NAME = 't_user';" \
      2>/dev/null || echo "0")"
  if [[ "$user_table_count" != "1" ]]; then
    echo "   [检测] shiqian_user 数据库或 t_user 表不存在"
    need_init=true
  fi

  # 检查 shiqian_resource 数据库及核心表
  resource_table_count="$(docker exec "$mysql_container" \
      mysql -u"$root_user" -p"$root_pass" -Nse \
      "SELECT COUNT(*) FROM information_schema.TABLES
       WHERE TABLE_SCHEMA = 'shiqian_resource' AND TABLE_NAME = 't_resource';" \
      2>/dev/null || echo "0")"
  if [[ "$resource_table_count" != "1" ]]; then
    echo "   [检测] shiqian_resource 数据库或 t_resource 表不存在"
    need_init=true
  fi

  if [ "$need_init" = true ]; then
    echo "   正在执行数据库初始化/修复脚本（docker/mysql/init/）..."

    local init_success=true

    for sql_file in docker/mysql/init/init.sql docker/mysql/init/z-demo-data.sql; do
      if [ -f "$sql_file" ]; then
        echo "     - 执行 $(basename "$sql_file")"
        if ! docker exec -i "$mysql_container" \
            mysql -u"$root_user" -p"$root_pass" < "$sql_file" 2>&1; then
          echo "     [错误] 执行 $sql_file 失败"
          init_success=false
        fi
      fi
    done

    if [ "$init_success" = true ]; then
      echo "   ✓ 数据库初始化/修复完成"
    else
      echo "   ✗ 数据库初始化过程中出现错误，建议手动检查日志"
    fi
  else
    echo "   ✓ shiqian_user 和 shiqian_resource 数据库结构均已就绪"
  fi
}

load_sdkman() {
  export SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
  if [ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
    set +u
    # shellcheck disable=SC1090
    source "$SDKMAN_DIR/bin/sdkman-init.sh"
    set -u
  fi
}

require_cmd() {
  local cmd="$1"
  local hint="$2"

  if ! has_cmd "$cmd"; then
    echo "缺少命令：$cmd" >&2
    echo "$hint" >&2
    exit 1
  fi
}

start_backend() {
  load_sdkman

  require_cmd docker "请先执行：./01_Environment.sh"
  require_cmd mvn "请先执行：./01_Environment.sh，或确认 Maven 已加入 PATH"
  if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose 不可用。" >&2
    echo "请先执行：./01_Environment.sh" >&2
    exit 1
  fi

  log "启动基础设施容器"
  docker compose up -d mysql redis nacos elasticsearch rabbitmq

  log "等待基础设施就绪"
  wait_container_healthy mysql "MySQL"

  # 自动检测并修复 MySQL 数据库（解决 volume 存在时初始化脚本不执行的问题）
  ensure_mysql_databases

  # 对已有数据卷执行幂等结构升级，补齐审核工作流字段。
  if [[ -f "docker/mysql/init/upgrade-resource-workflow.sql" ]]; then
    echo "→ 检查资源审核工作流字段..."
    docker exec -i shiqian-mysql \
      mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-root}" \
      < docker/mysql/init/upgrade-resource-workflow.sql
  fi

  wait_container_healthy redis "Redis"
  wait_container_running nacos "Nacos"
  wait_http "Nacos" "http://127.0.0.1:8848/nacos/"
  wait_container_healthy elasticsearch "Elasticsearch"
  wait_container_healthy rabbitmq "RabbitMQ"

  log "打包后端"
  export MAVEN_OPTS="${MAVEN_OPTS:--Xms256m -Xmx1024m -XX:MaxMetaspaceSize=384m}"
  mvn clean package -DskipTests

  log "启动后端服务"
  bash restart-backend.sh

  log "后端启动完成"
  echo "后端 Gateway:  http://localhost:8080"
  echo "用户服务:      http://localhost:8081"
  echo "资源服务:      http://localhost:8082"
  echo ""
  echo "如需查看后端日志：tail -f logs/fresh-*.log"
}

case "${1:-}" in
  "")
    start_backend
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
