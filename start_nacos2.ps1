$env:JAVA_HOME = "D:\tool\jdk-17"
$nacosHome = "D:\tool\nacos"

# Same as startup.cmd standalone mode
$jvmOpts = "-Xms512m -Xmx512m -Xmn256m"
$nacosOpts = "-Dnacos.standalone=true"
$nacosOpts += " -Dloader.path=$nacosHome/plugins,$nacosHome/plugins/health,$nacosHome/plugins/cmdb,$nacosHome/plugins/selector"
$nacosOpts += " -Dnacos.home=$nacosHome"
$nacosOpts += " -jar $nacosHome/target/nacos-server.jar"
$configOpts = "--spring.config.additional-location=file:$nacosHome/conf/"
$logOpts = "--logging.config=$nacosHome/conf/nacos-logback.xml"

$java = "$env:JAVA_HOME\bin\java.exe"
$args = "$jvmOpts $nacosOpts $configOpts $logOpts nacos.nacos"

Write-Host "Starting Nacos..."
Write-Host "Java: $java"
Write-Host "Jar: $nacosHome/target/nacos-server.jar"

$p = Start-Process -FilePath $java -ArgumentList $args -NoNewWindow -PassThru

Write-Host "PID: $($p.Id)"
Write-Host "Waiting..."
Start-Sleep -Seconds 15

$health = try { Invoke-WebRequest -Uri "http://localhost:8848/nacos/v1/console/health" -TimeoutSec 5 -UseBasicParsing } catch { $_.Exception.Message }
Write-Host "Health check: $health"
