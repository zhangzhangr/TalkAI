$env:JAVA_HOME = "D:\tool\jdk-17"

# Set Nacos to standalone mode
$env:MODE = "standalone"

# Start Nacos directly with java
$nacosHome = "D:\tool\nacos"
$classpath = "$nacosHome\conf;$nacosHome\target\*"

# Build the java command
$javaOpts = "-Dnacos.standalone=true -Dnacos.home=$nacosHome -Xms512m -Xmx512m"
$mainClass = "com.alibaba.nacos.Nacos"

Write-Host "Starting Nacos in standalone mode..."
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Nacos Home: $nacosHome"

# Start nacos process
$process = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
    -ArgumentList "$javaOpts -cp `"$classpath`" $mainClass" `
    -NoNewWindow -PassThru -RedirectStandardOutput "D:\tool\nacos\stdout.log" `
    -RedirectStandardError "D:\tool\nacos\stderr.log"

Write-Host "Nacos PID: $($process.Id)"
Write-Host "Waiting for Nacos to start..."
Start-Sleep -Seconds 5
