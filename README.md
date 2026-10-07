# 志愿填报系统(zytb)

面向**全国高考生**的志愿填报平台,微服务架构(与魄力学习平台同构):Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / JDK 17 / MyBatis-Plus / Nacos / Knife4j。

## 服务列表(端口与魄力学习一致,同机不可同时运行两套)

| 服务 | 端口 | 职责 |
|---|---|---|
| hbbxl-gateway | 8800 | 网关:路由/JWT鉴权/接口加密信封 |
| hbbxl-auth | 8801 | 认证:登录/登出/系统配置(账号由管理员开通,无自助注册) |
| hbbxl-user | 8802 | 用户:管理员开通账号/用户管理(admin/user双角色) |
| hbbxl-college | 8803 | 院校专业库(全国3075所院校/专业/章程/联系方式) |
| hbbxl-score | 8804 | 分数线/一分一段/位次查询 |
| hbbxl-aspiration | 8805 | 志愿表填报核心(志愿方案/批次) |
| hbbxl-recommend | 8806 | 智能推荐(冲稳保) |

## 环境约定

- **Nacos**: 81.70.105.12:8848,命名空间 `hbbxl`(生产)/`hbbxl-test`(本地开发)
- **MySQL**: 81.70.105.12:3306,库 **`zytb`**(表结构脚本见 docs/sql/rebuild/)
- **Redis**: 81.70.105.12:6379(token 前缀 `hbbxl:token:`)
- 多 Profile:bootstrap.yml(默认dev) + bootstrap-dev/test.yml(hbbxl-test) + bootstrap-prod.yml(hbbxl),容器内 ENV SPRING_PROFILES_ACTIVE=prod

## 快速开始

```powershell
mvn install -DskipTests          # 构建
powershell -ExecutionPolicy Bypass -File start-all.ps1          # 启动全部(本地=dev/hbbxl-test)
powershell -ExecutionPolicy Bypass -File start-all.ps1 auth      # 只启动单个
powershell -ExecutionPolicy Bypass -File kill-svc.ps1            # 全部停止
```

测试账号:admin/123456(管理员开通的普通用户账号初始密码为 123456)

接口入口:http://localhost:8800/api/... (Knife4j 文档:/api/{svc}/doc.html)

## 部署

docker/ 目录(Dockerfile.backend + docker-compose.yml + maven-settings.xml),镜像名 `hbbxl/<服务>:latest`,
Jenkinsfile 已配好仓库地址(zytb-java / zytb-vue)。

## 开发日志

见 docs/开发日志.md
