Write-Host "Extracting Nacos..."
Expand-Archive -Path "D:\tool\nacos.zip" -DestinationPath "D:\tool\" -Force
$dir = Get-ChildItem "D:\tool\" -Directory | Where-Object { $_.Name -like "*nacos*" } | Select-Object -First 1
Write-Host "Extracted to: $($dir.FullName)"

# Rename to a simple name
$target = "D:\tool\nacos"
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
Rename-Item $dir.FullName "nacos"
Write-Host "Renamed to: D:\tool\nacos"

# Clean up
Remove-Item "D:\tool\nacos.zip" -Force
Write-Host "Cleanup done"

# Verify
ls "D:\tool\nacos\bin\" | Select-Object Name
