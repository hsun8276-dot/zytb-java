// 志愿填报 CI/CD —— 只打包镜像，不自动部署
// 生产环境 = 开发者 Mac Docker Desktop（Apple Silicon → linux/arm64 + profile=prod）
// 开发环境 = Windows 本机 jar（profile=dev），不走本流水线
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        GIT_CREDENTIALS_ID = 'ad6fd866-21f0-4a9a-b4a6-8616e9d8bef0'
        BACKEND_REPO  = 'https://gitee.com/sh0818/zytb-java.git'
        FRONTEND_REPO = 'https://gitee.com/sh0818/zytb-vue.git'
        // Mac M 系列 Docker Desktop
        PLATFORM = 'linux/arm64'
        SERVICES = 'gateway auth user college score aspiration recommend'
        JENKINS_CONTAINER = '1Panel-jenkins-AGje'
    }

    stages {
        stage('检出前后端代码') {
            parallel {
                stage('后端') {
                    steps {
                        dir('backend') {
                            git branch: 'master', credentialsId: env.GIT_CREDENTIALS_ID, url: env.BACKEND_REPO
                        }
                    }
                }
                stage('前端') {
                    steps {
                        dir('frontend') {
                            git branch: 'master', credentialsId: env.GIT_CREDENTIALS_ID, url: env.FRONTEND_REPO
                        }
                    }
                }
            }
        }

        stage('Maven 打包 7 个微服务') {
            steps {
                dir('backend') {
                    sh '''
                        JHOME_HOST=$(docker inspect ${JENKINS_CONTAINER} --format '{{ range .Mounts }}{{ if eq .Destination "/var/jenkins_home" }}{{ .Source }}{{ end }}{{ end }}')
                        [ -z "$JHOME_HOST" ] && JHOME_HOST="/var/jenkins_home"
                        SRC_HOST="$JHOME_HOST/workspace/${JOB_NAME}/backend"
                        echo "==> 源码宿主机路径: $SRC_HOST"
                        docker run --rm -v "$SRC_HOST":/src -w /src \
                          -v hbbxl_m2:/root/.m2 \
                          -v "$SRC_HOST/docker/maven-settings.xml":/opt/settings.xml:ro \
                          --memory 1g maven:3.9-eclipse-temurin-17 \
                          mvn -q -s /opt/settings.xml -DskipTests package
                    '''
                }
            }
        }

        stage('构建镜像: 7 后端 + 1 前端 (arm64/prod→Mac)') {
            steps {
                dir('backend') {
                    sh '''
                        docker buildx create --name hbbxl-builder --use 2>/dev/null || docker buildx use hbbxl-builder
                        docker buildx inspect --bootstrap >/dev/null
                        for svc in $SERVICES; do
                          cp docker/Dockerfile.backend "hbbxl-$svc/Dockerfile"
                          docker buildx build --platform ${PLATFORM} --load \
                            --build-arg JAR_FILE="target/hbbxl-$svc-1.0.0-SNAPSHOT.jar" \
                            --build-arg SPRING_PROFILES_ACTIVE=prod \
                            -t "hbbxl/$svc:latest" \
                            "hbbxl-$svc"
                        done
                    '''
                }
                dir('frontend') {
                    sh '''
                        docker buildx create --name hbbxl-builder --use 2>/dev/null || docker buildx use hbbxl-builder
                        docker buildx build --platform ${PLATFORM} --load -t hbbxl/web:latest .
                    '''
                }
            }
        }

        stage('导出镜像包(可选下载到 Mac)') {
            steps {
                sh '''
                    OUT_DIR="$WORKSPACE/hbbxl-images"
                    mkdir -p "$OUT_DIR"
                    docker save -o "$OUT_DIR/hbbxl-mac-arm64.tar" \
                      hbbxl/gateway:latest \
                      hbbxl/auth:latest \
                      hbbxl/user:latest \
                      hbbxl/college:latest \
                      hbbxl/score:latest \
                      hbbxl/aspiration:latest \
                      hbbxl/recommend:latest \
                      hbbxl/web:latest
                    ls -lh "$OUT_DIR/hbbxl-mac-arm64.tar"
                    echo "==> 产物路径(Jenkins 工作区): $OUT_DIR/hbbxl-mac-arm64.tar"
                    echo "==> 拷到 Mac 后执行: docker load -i hbbxl-mac-arm64.tar"
                '''
                archiveArtifacts artifacts: 'hbbxl-images/hbbxl-mac-arm64.tar', fingerprint: true, allowEmptyArchive: false
            }
        }
    }

    post {
        success {
            echo '✅ 已构建 linux/arm64 + prod 镜像，并归档 hbbxl-mac-arm64.tar。下载后在 Mac: docker load -i hbbxl-mac-arm64.tar，再用 docker-compose.mac.yml 启动。'
        }
        failure { echo '❌ 构建失败, 请查看上方日志' }
    }
}
