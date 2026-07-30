#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESOURCE_DIR="$ROOT_DIR/shiqian-resource"
RESOURCE_JAR="$RESOURCE_DIR/target/shiqian-resource-1.0.0-SNAPSHOT.jar"

required_env=(
  JIMENG_DB_HOST
  JIMENG_DB_NAME
  JIMENG_DB_USER
  JIMENG_DB_PASSWORD
  CAMPUSHUB_DB_PASSWORD
)

for variable_name in "${required_env[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "缺少环境变量：$variable_name" >&2
    exit 2
  fi
done

cd "$ROOT_DIR"

echo "编译即梦安全导入器..."
mvn -q -pl shiqian-resource -am -DskipTests compile

if [[ ! -s "$RESOURCE_JAR" ]]; then
  echo "尚无资源服务运行包，执行一次初始打包..."
  mvn -q -pl shiqian-resource -am -DskipTests package
fi

exec java \
  -Dloader.main=com.shiqian.resource.tools.JimengPromptImporter \
  -Dloader.path="$RESOURCE_DIR/target/classes" \
  -cp "$RESOURCE_JAR" \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  "$@"
