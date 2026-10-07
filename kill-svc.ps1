# 杀掉全部 hbbxl 本地服务进程(按 jar 路径匹配, 不误伤 IDEA)
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -match 'hbbxl-[a-z-]+-1\.0\.0-SNAPSHOT\.jar' } |
  ForEach-Object { Write-Host ("杀 " + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force }
