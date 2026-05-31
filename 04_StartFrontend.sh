#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$ROOT_DIR/shiqian-frontend"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$LOG_DIR/frontend.pid"
LOG_FILE="$LOG_DIR/fresh-frontend.log"

log() {
  echo ""
  echo "=== $* ==="
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

load_nvm() {
  export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
  if [[ -s "$NVM_DIR/nvm.sh" ]]; then
    set +u
    # shellcheck disable=SC1090
    . "$NVM_DIR/nvm.sh"
    set -u
  fi
}

stop_old_frontend() {
  local pid=""

  mkdir -p "$LOG_DIR"
  if [[ -f "$PID_FILE" ]]; then
    pid="$(cat "$PID_FILE" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "停止旧前端进程 PID $pid ..."
      kill "$pid" 2>/dev/null || true
      sleep 2
    fi
  fi

  pkill -f 'npm run dev' 2>/dev/null || true
  pkill -f 'vite --host 0.0.0.0' 2>/dev/null || true
  rm -f "$PID_FILE"
}

load_nvm

if ! has_cmd npm; then
  echo "缺少命令：npm" >&2
  echo "请先执行：./01_Environment.sh，或确认 Node.js/npm 已加入 PATH" >&2
  exit 1
fi

if [[ ! -d "$FRONTEND_DIR" ]]; then
  echo "前端目录不存在：$FRONTEND_DIR" >&2
  exit 1
fi

log "安装前端依赖"
cd "$FRONTEND_DIR"
npm install

log "启动前端服务"
stop_old_frontend
cd "$FRONTEND_DIR"
nohup npm run dev > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

sleep 3

log "前端启动完成"
echo "前端地址: http://localhost:5173"
echo "日志文件: $LOG_FILE"
echo "PID 文件: $PID_FILE"
echo ""
echo "如需查看前端日志：tail -f logs/fresh-frontend.log"
