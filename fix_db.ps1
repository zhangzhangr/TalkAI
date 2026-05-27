$jar = Get-ChildItem "D:\maven\repository3\com\mysql\mysql-connector-j" -Recurse -Filter "mysql-connector-j-*.jar" | Select-Object -First 1

if (-not $jar) {
    Write-Host "MySQL driver JAR not found, downloading..."
    $wc = New-Object System.Net.WebClient
    $wc.DownloadFile("https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar", "D:\tool\mysql-connector-j.jar")
    $jarPath = "D:\tool\mysql-connector-j.jar"
} else {
    $jarPath = $jar.FullName
}

Write-Host "Using MySQL driver: $jarPath"

$sql = @"
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS system_prompt TEXT DEFAULT NULL COMMENT '系统提示词' AFTER model;
"@

# Write SQL to temp file
$sqlFile = "D:\tool\fix_db.sql"
$sql | Out-File -FilePath $sqlFile -Encoding utf8

# Execute using JDBC
$java = "D:\tool\jdk-17\bin\java.exe"
$result = & $java -cp $jarPath -Dfile.encoding=UTF-8 `
    -e "try {
        Class.forName(\"com.mysql.cj.jdbc.Driver\");
        var conn = java.sql.DriverManager.getConnection(\"jdbc:mysql://localhost:3306/talkai?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8\", \"root\", \"root\");
        var stmt = conn.createStatement();
        stmt.executeUpdate(\"ALTER TABLE conversation ADD COLUMN system_prompt TEXT DEFAULT NULL COMMENT '系统提示词' AFTER model\");
        System.out.println(\"Column 'system_prompt' added successfully.\");
        stmt.close();
        conn.close();
    } catch (com.mysql.cj.jdbc.exceptions.MysqlDataTruncation e) {
        if (e.getMessage().contains(\"Duplicate column name\")) {
            System.out.println(\"Column already exists, skipping.\");
        } else {
            throw e;
        }
    }" 2>&1

Write-Host "Result: $result"
Remove-Item $sqlFile -Force -ErrorAction SilentlyContinue
