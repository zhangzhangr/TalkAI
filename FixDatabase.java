import java.sql.*;

public class FixDatabase {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection conn = DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/talkai?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
            "root", "root");
        Statement stmt = conn.createStatement();
        try {
            stmt.executeUpdate("ALTER TABLE conversation ADD COLUMN system_prompt TEXT DEFAULT NULL COMMENT 'system prompt' AFTER model");
            System.out.println("Column 'system_prompt' added successfully.");
        } catch (Exception e) {
            if (e.getMessage().contains("Duplicate column name")) {
                System.out.println("Column already exists, skipping.");
            } else {
                throw e;
            }
        }
        stmt.close();
        conn.close();
    }
}
