package com.talkai.agent.tool;

import com.talkai.agent.protocol.McpToolCallResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchTool implements ToolDefinition {

    private final WebClient.Builder webClientBuilder;

    @Value("${talkai.agent.tools.search.api-key:}")
    private String apiKey;

    @Value("${talkai.agent.tools.search.base-url:https://api.bing.microsoft.com/v7.0}")
    private String baseUrl;

    @Override
    public String getName() { return "web_search"; }

    @Override
    public String getDescription() { return "Search the web for information (Bing)"; }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search query string"),
                        "num", Map.of("type", "integer", "description", "Number of results, default 5")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public Mono<McpToolCallResult> execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        int num = arguments.containsKey("num") ? ((Number) arguments.get("num")).intValue() : 5;

        // Bing Web Search API v7
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        WebClient.RequestHeadersSpec<?> spec = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("count", num)
                        .queryParam("mkt", "zh-CN")
                        .build());
        if (apiKey != null && !apiKey.isEmpty()) {
            spec.header("Ocp-Apim-Subscription-Key", apiKey);
        }

        return spec.retrieve()
                .bodyToMono(Map.class)
                .map(data -> formatResult(data, num))
                .onErrorResume(e -> {
                    log.error("Search failed for query: {}", query, e);
                    return Mono.just(McpToolCallResult.builder()
                            .content(List.of(McpToolCallResult.McpContentItem.builder()
                                    .type("text").text("Search failed: " + e.getMessage()).build()))
                            .isError(true)
                            .build());
                });
    }

    @SuppressWarnings("unchecked")
    private McpToolCallResult formatResult(Map<String, Object> data, int num) {
        StringBuilder sb = new StringBuilder();
        sb.append("Search results:\n\n");

        Map<String, Object> webPages = (Map<String, Object>) data.get("webPages");
        if (webPages != null) {
            List<Map<String, Object>> results = (List<Map<String, Object>>) webPages.get("value");
            if (results != null) {
                int count = 0;
                for (Map<String, Object> r : results) {
                    if (count >= num) break;
                    count++;
                    sb.append(count).append(". ").append(r.get("name")).append("\n");
                    sb.append("   ").append(r.get("snippet")).append("\n");
                    sb.append("   URL: ").append(r.get("url")).append("\n\n");
                }
            }
        }

        if (sb.toString().equals("Search results:\n\n")) {
            sb.append("No results found. (Bing Search requires a valid API key. Set SEARCH_API_KEY env var.)");
        }

        return McpToolCallResult.builder()
                .content(List.of(McpToolCallResult.McpContentItem.builder()
                        .type("text").text(sb.toString()).build()))
                .isError(false)
                .build();
    }
}
