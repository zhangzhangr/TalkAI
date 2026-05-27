$url = "https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.zip"
$out = "D:\tool\nacos.zip"
Write-Host "Downloading Nacos 2.3.2..."
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Invoke-WebRequest -Uri $url -OutFile $out -ErrorAction Stop
Write-Host "Download complete"
