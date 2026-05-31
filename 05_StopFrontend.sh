#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$LOG_DIR/frontend.pid"

log() {
  echo ""
  echo "=== $* ==="
}

stop_pid_file() {
  local pid=""

  [[ -f "$PID_FILE" ]] || return 0
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -z "$pid" ]]; then
    rm -f "$PID_FILE"
    return 0
  fi

  if kill -0 "$pid" 2>/dev/null; then
    echo "停止前端进程 PID $pid ..."
    kill "$pid" 2>/dev/null || true
  fi
}

wait_frontend_exit() {
  local deadline=$((SECONDS + 8))

  while pgrep -f 'npm run dev|vite --host 0.0.0.0' >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "仍有前端进程未退出，强制停止 ..."
      pkill -9 -f 'npm run dev' 2>/dev/null || true
      pkill -9 -f 'vite --host 0.0.0.0' 2>/dev/null || true
      return 0
    fi
    sleep 1
  done
}

log "停止前端服务"
stop_pid_file
pkill -f 'npm run dev' 2>/dev/null || true
pkill -f 'vite --host 0.0.0.0' 2>/dev/null || true
wait_frontend_exit
rm -f "$PID_FILE"

log "前端已停止"
