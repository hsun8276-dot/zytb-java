# 构建与部署一键脚本(服务器上执行, 或由Jenkins调用)
# 用法: bash docker/build-and-deploy.sh [backend_dir] [frontend_dir]
#   backend_dir  后端仓库目录(默认当前目录)
#   frontend_dir 前端仓库目录(默认 ../hbbxl_-vue_-edy 或 $WEB_DIR)
set -e

BACKEND_DIR="${1:-$(pwd)}"
WEB_DIR="${2:-${WEB_DIR:-../hbbxl_-vue_-edy}}"
MVN_IMAGE="maven:3.9-eclipse-temurin-17"
NODE_IMAGE="node:20-alpine"
COMPOSE_FILE="$BACKEND_DIR/docker/docker-compose.yml"

echo "==> [1/5] Maven 构建后端 13 个服务 jar"
docker run --rm \
  -v "$BACKEND_DIR":/src -w /src \
  -v hbbxl_m2:/root/.m2 \
  -v "$BACKEND_DIR/docker/maven-settings.xml":/opt/settings.xml:ro \
  "$MVN_IMAGE" mvn -q -s /opt/settings.xml -DskipTests package

echo "==> [2/5] 构建后端镜像"
SERVICES="gateway auth user category question exam wrong-book video ai statistics notification school ocr"
for svc in $SERVICES; do
  JAR="hbbxl-$svc/target/hbbxl-$svc-1.0.0-SNAPSHOT.jar"
  echo "  - hbbxl/$svc:latest  ($JAR)"
  docker build -f "$BACKEND_DIR/docker/Dockerfile.backend" \
    --build-arg JAR_FILE="$JAR" \
    -t "hbbxl/$svc:latest" "$BACKEND_DIR"
done

echo "==> [3/5] npm 构建前端"
docker run --rm \
  -v "$WEB_DIR":/src -w /src \
  -v hbbxl_npm_cache:/root/.npm \
  -e npm_config_registry=https://mirrors.cloud.tencent.com/npm/ \
  "$NODE_IMAGE" sh -c "npm ci && npm run build"

echo "==> [4/5] 构建前端镜像"
docker build -t hbbxl/web:latest "$WEB_DIR"

echo "==> [5/5] 启动/更新容器"
cd "$BACKEND_DIR/docker"
if [ -f .env ]; then ENV_ARGS="--env-file .env"; else ENV_ARGS=""; fi
# 默认不启动 OCR(按需): 全量启动加 --profile full
docker compose -p hbbxl $ENV_ARGS -f docker-compose.yml up -d

echo "==> 完成。前端地址: http://\$(curl -s ifconfig.me):\$(grep NGINX_PORT .env 2>/dev/null | cut -d= -f2 || echo 80)"
docker compose -p hbbxl ps
