# Copy file first to release any lock, then extract
$src = "D:\tool\nacos.zip"
$tmp = "D:\tool\nacos_tmp.zip"
Copy-Item $src $tmp -Force
Start-Sleep -Seconds 2

Write-Host "Extracting Nacos..."
[System.Reflection.Assembly]::LoadWithPartialName("System.IO.Compression.FileSystem") | Out-Null
[System.IO.Compression.ZipFile]::ExtractToDirectory($tmp, "D:\tool\nacos_extract")
Write-Host "Extracted"

$dir = Get-ChildItem "D:\tool\nacos_extract" -Directory | Select-Object -First 1
if ($dir) {
    $target = "D:\tool\nacos"
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    Move-Item $dir.FullName $target -Force
    Write-Host "Moved to D:\tool\nacos"
} else {
    # The zip might have files directly (no subdir)
    $target = "D:\tool\nacos"
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    Move-Item "D:\tool\nacos_extract" $target -Force
    Write-Host "Moved flat to D:\tool\nacos"
}

# Cleanup
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
if (Test-Path "D:\tool\nacos_extract") { Remove-Item "D:\tool\nacos_extract" -Recurse -Force }
Remove-Item $src -Force -ErrorAction SilentlyContinue

Write-Host "Done. Contents:"
Get-ChildItem "D:\tool\nacos\bin\*.cmd" | Select-Object Name
