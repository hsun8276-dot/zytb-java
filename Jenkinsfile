// 志愿填报 CI/CD —— Jenkins 只负责构建镜像, 部署由手动执行
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
        PLATFORM = 'linux/amd64'
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
                        # 自动发现 jenkins_home 的宿主机真实路径(兼容1Panel数据卷)
                        JHOME_HOST=$(docker inspect ${JENKINS_CONTAINER} --format '{{ range .Mounts }}{{ if eq .Destination "/var/jenkins_home" }}{{ .Source }}{{ end }}{{ end }}')
                        [ -z "$JHOME_HOST" ] && JHOME_HOST="/var/jenkins_home"
                        SRC_HOST="$JHOME_HOST/workspace/${JOB_NAME}/backend"
                        echo "==> 源码宿主机路径: $SRC_HOST"
                        docker run --rm -v "$SRC_HOST":/src -w /src                           -v hbbxl_m2:/root/.m2                           -v "$SRC_HOST/docker/maven-settings.xml":/opt/settings.xml:ro                           --memory 1g maven:3.9-eclipse-temurin-17                           mvn -q -s /opt/settings.xml -DskipTests package
                    '''
                }
            }
        }

        stage('构建镜像: 7 后端 + 1 前端') {
            steps {
                dir('backend') {
                    sh '''
                        for svc in $SERVICES; do
                          cp docker/Dockerfile.backend "hbbxl-$svc/Dockerfile"
                          docker build --platform ${PLATFORM}                             --build-arg JAR_FILE="target/hbbxl-$svc-1.0.0-SNAPSHOT.jar"                             -t "hbbxl/$svc:latest"                             "hbbxl-$svc"
                        done
                    '''
                }
                dir('frontend') {
                    sh 'docker build --platform ${PLATFORM} -t hbbxl/web:latest .'
                }
            }
        }
    }

    post {
        success { echo '✅ 8 个镜像构建完成, 可手动部署: cd <后端仓库>/docker && docker compose -p hbbxl -f docker-compose.yml --env-file .env up -d --pull never' }
        failure { echo '❌ 构建失败, 请查看上方日志' }
    }
}
