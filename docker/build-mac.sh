#!/usr/bin/env bash
# 在 Mac（Apple Silicon）上本地打包 7 后端 + 1 前端镜像（linux/arm64 + prod）
# 用法:
#   bash docker/build-mac.sh [/path/to/zytb-java] [/path/to/zytb-vue]
set -euo pipefail

BACKEND_DIR="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
WEB_DIR="${2:-$(cd "$BACKEND_DIR/../zytb-vue" 2>/dev/null && pwd || true)}"
PLATFORM="${DOCKER_PLATFORM:-linux/arm64}"
SERVICES=(gateway auth user college score aspiration recommend)

if [[ -z "${WEB_DIR}" || ! -d "${WEB_DIR}" ]]; then
  echo "找不到前端目录，请传入: bash docker/build-mac.sh <后端目录> <前端目录>"
  exit 1
fi

echo "==> 后端: $BACKEND_DIR"
echo "==> 前端: $WEB_DIR"
echo "==> 平台: $PLATFORM"

echo "==> [1/3] Maven package"
docker run --rm --platform "$PLATFORM" \
  -v "$BACKEND_DIR":/src -w /src \
  -v hbbxl_m2:/root/.m2 \
  -v "$BACKEND_DIR/docker/maven-settings.xml":/opt/settings.xml:ro \
  maven:3.9-eclipse-temurin-17 \
  mvn -q -s /opt/settings.xml -DskipTests package

echo "==> [2/3] 构建后端镜像"
for svc in "${SERVICES[@]}"; do
  cp "$BACKEND_DIR/docker/Dockerfile.backend" "$BACKEND_DIR/hbbxl-$svc/Dockerfile"
  docker build --platform "$PLATFORM" \
    --build-arg JAR_FILE="target/hbbxl-$svc-1.0.0-SNAPSHOT.jar" \
    --build-arg SPRING_PROFILES_ACTIVE=prod \
    -t "hbbxl/$svc:latest" \
    "$BACKEND_DIR/hbbxl-$svc"
done

echo "==> [3/3] 构建前端镜像"
docker build --platform "$PLATFORM" -t hbbxl/web:latest "$WEB_DIR"

echo "==> 完成。镜像列表:"
docker images 'hbbxl/*' --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}'
echo
echo "启动:"
echo "  cd $BACKEND_DIR/docker"
echo "  cp -n .env.mac.example .env.mac"
echo "  docker compose -p hbbxl -f docker-compose.mac.yml --env-file .env.mac up -d"
