#!/usr/bin/env bash
set -Eeuo pipefail

echo "=== CNB / Lightning AI Studio 精简环境初始化开始 ==="

# =========================
# 0. CNB.cool 基础环境预处理
# =========================
echo "=== 基础环境预处理 ==="

if ! command -v sudo >/dev/null 2>&1; then
  echo "检测到 sudo 不存在，正在安装 sudo nano zip..."
  apt update -qq
  apt install -y sudo nano zip
else
  echo "sudo 已存在，跳过 sudo 安装"
  sudo apt update -qq
  sudo apt install -y nano zip
fi

# =========================
# 1. 通用工具函数
# =========================
log()  { echo -e "$*"; }
ok()   { echo "✅ $*"; }
warn() { echo "⚠️  $*"; }
err()  { echo "❌ $*" >&2; }

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

ensure_docker() {
  if has_cmd docker && docker compose version >/dev/null 2>&1; then
    ok "Docker 和 Docker Compose 已安装"
    return
  fi

  log "=== 安装 Docker ==="
  sudo apt update -qq
  sudo apt install -y ca-certificates curl gnupg
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo rm -f /etc/apt/keyrings/docker.gpg
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  . /etc/os-release
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt update -qq
  sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

  if ! groups "$USER" | grep -qw docker; then
    sudo usermod -aG docker "$USER" || true
    warn "已把当前用户加入 docker 组。如果后续 docker 权限失败，请重新登录后再执行启动脚本。"
  fi

  ok "Docker 安装完成"
}

append_if_missing() {
  local line="$1"
  local file="$2"
  touch "$file"
  grep -qxF "$line" "$file" 2>/dev/null || echo "$line" >> "$file"
}

persist_line_to_shells() {
  local line="$1"
  append_if_missing "$line" "$HOME/.bashrc"
  append_if_missing "$line" "$HOME/.zshrc"
}

persist_path_to_shells() {
  local p="$1"
  persist_line_to_shells "export PATH=\"$p:\$PATH\""
}

remove_line_from_shells() {
  local line="$1"
  for file in "$HOME/.bashrc" "$HOME/.zshrc"; do
    [[ -f "$file" ]] || continue
    grep -vxF "$line" "$file" > "$file.tmp" 2>/dev/null && mv "$file.tmp" "$file"
    rm -f "$file.tmp"
  done
}

prepend_path_now() {
  local p="$1"
  case ":${PATH:-}:" in
    *":$p:"*) ;;
    *) export PATH="$p:${PATH:-}" ;;
  esac
}

repair_zsh_prompt_env() {
  log "=== 修复 zsh RPROMPT / nounset 问题 ==="

  touch "$HOME/.zshrc"
  cp "$HOME/.zshrc" "$HOME/.zshrc.bak.$(date +%F_%H%M%S)" 2>/dev/null || true

  sed -i 's/ && set -u//g' "$HOME/.zshrc" 2>/dev/null || true
  sed -i 's/\[\[ -s "\$SDKMAN_DIR\/bin\/sdkman-init.sh" \]\] && set +u && source "\$SDKMAN_DIR\/bin\/sdkman-init.sh"/[[ -s "$SDKMAN_DIR\/bin\/sdkman-init.sh" ]] \&\& source "$SDKMAN_DIR\/bin\/sdkman-init.sh"/g' "$HOME/.zshrc" 2>/dev/null || true

  append_if_missing 'unsetopt nounset 2>/dev/null || true' "$HOME/.zshrc"
  append_if_missing 'typeset -g RPROMPT="${RPROMPT-}"' "$HOME/.zshrc"

  ok "zsh RPROMPT / nounset 修复完成"
}

trap 'err "脚本在第 $LINENO 行失败。"' ERR

# =========================
# 2. 基础依赖
# =========================
log "=== 安装基础依赖 ==="

sudo apt update -qq
sudo apt install -y \
  ca-certificates curl wget git zip unzip nano vim \
  build-essential pkg-config libssl-dev

ok "基础依赖安装完成"

# =========================
# 3. Java + Maven + Gradle：通过 SDKMAN 安装
# =========================
log "=== 安装 SDKMAN 并配置 Java / Maven / Gradle ==="

export SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"

if [[ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
  log "正在安装 SDKMAN..."
  curl -s "https://get.sdkman.io" | bash
  ok "SDKMAN 安装完成"
else
  ok "SDKMAN 已存在，跳过安装"
fi

log "正在加载 SDKMAN 环境..."
set +u
# shellcheck disable=SC1090
source "$SDKMAN_DIR/bin/sdkman-init.sh"
set -u

sdk_safe() {
  set +u
  sdk "$@"
  set -u
}

log "正在安装 Java 21 Temurin..."

sdk_safe install java 21.0.2-tem || {
  warn "Java 21.0.2-tem 安装失败，尝试安装最新版 21-tem..."
  sdk_safe install java 21-tem || {
    warn "21-tem 也失败，尝试安装 SDKMAN 默认 Java..."
    sdk_safe install java || true
  }
}

log "正在安装 Maven..."
sdk_safe install maven || true

log "正在安装 Gradle..."
sdk_safe install gradle || true

JAVA_HOME="$SDKMAN_DIR/candidates/java/current"
export JAVA_HOME
prepend_path_now "$JAVA_HOME/bin"

persist_line_to_shells "export SDKMAN_DIR=\"$SDKMAN_DIR\""
remove_line_from_shells '[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"'
persist_line_to_shells 'if [[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then if [[ $- == *u* ]]; then set +u; source "$SDKMAN_DIR/bin/sdkman-init.sh"; set -u; else source "$SDKMAN_DIR/bin/sdkman-init.sh"; fi; fi'
persist_line_to_shells "export JAVA_HOME=\"$JAVA_HOME\""
persist_line_to_shells "export PATH=\"$JAVA_HOME/bin:\$PATH\""

ok "JAVA_HOME = $JAVA_HOME"
ok "Java / Maven / Gradle 安装完成"

# =========================
# 4. uv
# =========================
log "=== 安装 uv ==="

prepend_path_now "$HOME/.local/bin"
persist_path_to_shells "$HOME/.local/bin"

if ! has_cmd uv; then
  curl -LsSf https://astral.sh/uv/install.sh | sh
  ok "uv 安装成功"
else
  ok "uv 已安装，跳过"
fi

# =========================
# 5. nvm + Node.js
# =========================
log "=== 安装 nvm + Node.js ==="

export NVM_DIR="$HOME/.nvm"

if [[ ! -s "$NVM_DIR/nvm.sh" ]]; then
  log "正在安装 nvm..."
  curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/master/install.sh | bash
  ok "nvm 安装完成"
else
  ok "nvm 已存在，跳过安装"
fi

persist_line_to_shells 'export NVM_DIR="$HOME/.nvm"'
persist_line_to_shells '[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"'
persist_line_to_shells '[ -s "$NVM_DIR/bash_completion" ] && . "$NVM_DIR/bash_completion"'

set +u
# shellcheck disable=SC1090
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
# shellcheck disable=SC1090
[ -s "$NVM_DIR/bash_completion" ] && . "$NVM_DIR/bash_completion" || true
set -u

log "正在通过 nvm 安装并切换到最新 LTS..."
nvm install --lts
nvm alias default 'lts/*'
nvm use default

ok "Node 当前版本: $(node -v)"
ok "npm 当前版本: $(npm -v)"

persist_line_to_shells 'if command -v nvm >/dev/null 2>&1; then nvm use default >/dev/null 2>&1 || true; fi'

# =========================
# 6. Docker
# =========================
ensure_docker

# =========================
# 7. 前端依赖
# =========================
log "=== 安装前端依赖 ==="

if [[ -f "shiqian-frontend/package-lock.json" ]]; then
  (cd shiqian-frontend && npm ci)
  ok "前端依赖安装完成"
else
  warn "未找到 shiqian-frontend/package-lock.json，跳过前端依赖安装"
fi

# =========================
# 8. 修复 zsh 交互环境
# =========================
repair_zsh_prompt_env

# =========================
# 9. 验证
# =========================
log "=== 安装完成验证 ==="

echo "Java:"
java -version 2>&1 | head -n 3 || true

echo "Maven:"
mvn -version 2>&1 | head -n 2 || true

echo "Gradle:"
gradle -version 2>&1 | head -n 3 || true

echo "Node:"
node --version || true

echo "npm:"
npm --version || true

echo "uv:"
uv --version || true

echo "=== 精简环境初始化完成 ==="
echo "下一步启动后端：./02_StartBackend.sh"
echo "提示：如果你是 zsh 终端，执行：source ~/.zshrc"
echo "提示：如果你是 bash 终端，执行：source ~/.bashrc"
echo "如果之前出现 RPROMPT: parameter not set，请重新打开一个终端再测试。"
