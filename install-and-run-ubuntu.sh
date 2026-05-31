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

wait_http() {
  local name="$1"
  local url="$2"
  local max="${3:-60}"

  for i in $(seq 1 "$max"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name 已就绪"
      return 0
    fi
    echo "等待 $name $i/$max..."
    sleep 2
  done

  echo "$name 未在预期时间内就绪：$url" >&2
  return 1
}

wait_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"
  local max="${4:-60}"

  for i in $(seq 1 "$max"); do
    if timeout 2 bash -c "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null; then
      echo "$name 已就绪"
      return 0
    fi
    echo "等待 $name $i/$max..."
    sleep 2
  done

  echo "$name 未在预期时间内就绪：$host:$port" >&2
  return 1
}

ensure_docker() {
  if has_cmd docker && docker compose version >/dev/null 2>&1; then
    echo "Docker 和 Docker Compose 已安装"
    return
  fi

  log "安装 Docker"
  if ! has_cmd sudo; then
    apt-get update
    apt-get install -y sudo
  fi

  sudo apt-get update
  sudo apt-get install -y ca-certificates curl gnupg
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  . /etc/os-release
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

  if groups "$USER" | grep -qw docker; then
    return
  fi

  sudo usermod -aG docker "$USER" || true
  echo "已把当前用户加入 docker 组。如果后续 docker 权限失败，请重新登录后再执行本脚本。"
}

log "安装基础开发环境"
bash 1.sh

export SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
if [ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
  set +u
  # shellcheck disable=SC1090
  source "$SDKMAN_DIR/bin/sdkman-init.sh"
  set -u
fi

export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  set +u
  # shellcheck disable=SC1090
  source "$NVM_DIR/nvm.sh"
  set -u
fi

ensure_docker

log "启动基础设施容器"
docker compose up -d mysql redis nacos elasticsearch rabbitmq

log "等待基础设施就绪"
wait_tcp "MySQL" 127.0.0.1 3306 60
wait_tcp "Redis" 127.0.0.1 6379 30
wait_tcp "RabbitMQ" 127.0.0.1 5672 60
wait_http "Nacos" "http://127.0.0.1:8848/nacos/" 60
wait_http "Elasticsearch" "http://127.0.0.1:9200/" 60

log "打包后端"
mvn clean package -DskipTests

log "启动后端服务"
bash restart-backend.sh

log "安装前端依赖"
(cd shiqian-frontend && npm ci)

log "启动完成"
echo "后端 Gateway:  http://localhost:8080"
echo "用户服务:      http://localhost:8081"
echo "资源服务:      http://localhost:8082"
echo "前端开发服务:  cd shiqian-frontend && npm run dev"
echo ""
echo "如需查看后端日志：tail -f logs/fresh-*.log"
