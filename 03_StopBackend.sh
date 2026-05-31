#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

log() {
  echo ""
  echo "=== $* ==="
}

stop_pid_file() {
  local name="$1"
  local pid_file="$2"
  local pid=""

  [[ -f "$pid_file" ]] || return 0
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -z "$pid" ]]; then
    rm -f "$pid_file"
    return 0
  fi

  if kill -0 "$pid" 2>/dev/null; then
    echo "停止 $name PID $pid ..."
    kill "$pid" 2>/dev/null || true
  fi
}

wait_java_exit() {
  local deadline=$((SECONDS + 12))

  while ps aux | grep -E 'java.*target/shiqian-(user|resource|gateway)' | grep -v grep >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "仍有后端 Java 进程未退出，强制停止 ..."
      pkill -9 -f 'java.*target/shiqian-(user|resource|gateway)' 2>/dev/null || true
      return 0
    fi
    sleep 1
  done
}

log "停止后端 Java 服务"
stop_pid_file "shiqian-gateway" "logs/gateway.pid"
stop_pid_file "shiqian-resource" "logs/resource.pid"
stop_pid_file "shiqian-user" "logs/user.pid"

pkill -f 'java.*target/shiqian-(user|resource|gateway)' 2>/dev/null || true
wait_java_exit
rm -f logs/gateway.pid logs/resource.pid logs/user.pid

log "停止 Docker Compose 容器"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  docker compose down
else
  echo "Docker Compose 不可用，跳过容器停止。"
fi

log "停止完成"
