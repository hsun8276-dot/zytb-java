# 志愿填报平台 - Windows 本地开发启动(profile=dev)
# 用法: powershell -ExecutionPolicy Bypass -File start-all.ps1
#       powershell -ExecutionPolicy Bypass -File start-all.ps1 auth
$REPO = "F:\hbbxl"
$LOGS = "$REPO\logs"
if (-not (Test-Path $LOGS)) { New-Item -ItemType Directory -Path $LOGS | Out-Null }

# bootstrap 默认连 Mac 容器名；Windows 开发必须覆盖为远程/本机中间件
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:NACOS_ADDR = "81.70.105.12:8848"
$env:NACOS_NAMESPACE = "hbbxl-test"
$env:NACOS_USERNAME = "nacos"
$env:NACOS_PASSWORD = "Wyl123..."

$SERVICES = @(
    @{name="gateway";      port=8800},
    @{name="auth";         port=8801},
    @{name="user";         port=8802},
    @{name="college";      port=8803},
    @{name="score";        port=8804},
    @{name="aspiration";   port=8805},
    @{name="recommend";    port=8806}
)

$only = if ($args.Count -gt 0) { $args[0] } else { $null }

foreach ($svc in $SERVICES) {
    $name = $svc.name
    $port = $svc.port
    if ($only -and $name -ne $only) { continue }
    $jar = "$REPO\hbbxl-$name\target\hbbxl-$name-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { Write-Host "跳过 $name (jar不存在)"; continue }
    $listening = netstat -ano | Select-String ":$port .*LISTENING"
    if ($listening) { Write-Host "$name 已在运行(端口$port)"; continue }
    Write-Host "启动 $name (端口$port, profile=dev/hbbxl-test)..."
    Start-Process -FilePath "java" `
        -ArgumentList "-Xms128m","-Xmx256m","-XX:+UseSerialGC","-XX:MaxMetaspaceSize=256m","-XX:MetaspaceSize=128m",`
            "-Dspring.profiles.active=dev",`
            "-jar",$jar `
        -WorkingDirectory $REPO `
        -WindowStyle Hidden `
        -RedirectStandardOutput "$LOGS\$name.log" `
        -RedirectStandardError "$LOGS\$name.err.log"
    Start-Sleep -Seconds 2
}
Write-Host "全部服务启动指令已发出,约40秒后就绪(本地=测试环境hbbxl-test)"
