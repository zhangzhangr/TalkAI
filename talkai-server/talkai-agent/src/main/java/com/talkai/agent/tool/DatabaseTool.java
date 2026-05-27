package com.talkai.agent.tool;

import com.talkai.agent.protocol.McpToolCallResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseTool implements ToolDefinition {

    private final DataSource dataSource;

    @Override
    public String getName() { return "database_query"; }

    @Override
    public String getDescription() { return "Execute a read-only SQL SELECT query against the TalkAI database"; }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "SELECT SQL query to execute (read-only)")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public Mono<McpToolCallResult> execute(Map<String, Object> arguments) {
        String sql = (String) arguments.get("query");
        if (sql == null || sql.isBlank()) {
            return Mono.just(errorResult("Query cannot be empty"));
        }

        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("SHOW") && !trimmed.startsWith("DESCRIBE")) {
            return Mono.just(errorResult("Only read-only queries are allowed (SELECT, SHOW, DESCRIBE)"));
        }

        return Mono.fromCallable(() -> executeQuery(sql))
                .onErrorResume(e -> Mono.just(errorResult("Query failed: " + e.getMessage())));
    }

    private McpToolCallResult executeQuery(String sql) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            StringBuilder sb = new StringBuilder();
            sb.append("Query results:\n");

            // Build header
            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                headers.add(meta.getColumnName(i));
            }
            sb.append(String.join(" | ", headers)).append("\n");
            sb.append("-".repeat(headers.stream().mapToInt(String::length).sum() + (headers.size() - 1) * 3)).append("\n");

            int rowCount = 0;
            int maxRows = 50;
            while (rs.next() && rowCount < maxRows) {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object val = rs.getObject(i);
                    values.add(val != null ? val.toString() : "NULL");
                }
                sb.append(String.join(" | ", values)).append("\n");
                rowCount++;
            }

            sb.append("\nRows returned: ").append(rowCount);
            if (rowCount >= maxRows) {
                sb.append(" (limited to ").append(maxRows).append(" rows)");
            }

            return McpToolCallResult.builder()
                    .content(List.of(McpToolCallResult.McpContentItem.builder()
                            .type("text").text(sb.toString()).build()))
                    .isError(false)
                    .build();
        }
    }

    private McpToolCallResult errorResult(String message) {
        return McpToolCallResult.builder()
                .content(List.of(McpToolCallResult.McpContentItem.builder()
                        .type("text").text(message).build()))
                .isError(true)
                .build();
    }
}
