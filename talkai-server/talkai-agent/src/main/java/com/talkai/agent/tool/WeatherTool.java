package com.talkai.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class WeatherTool implements ToolDefinition {

    private final WebClient.Builder webClientBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${talkai.agent.tools.weather.api-key:}")
    private String apiKey;

    @Value("${talkai.agent.tools.weather.base-url:https://api.openweathermap.org/data/2.5}")
    private String baseUrl;

    @Override
    public String getName() { return "weather"; }

    @Override
    public String getDescription() { return "Get current weather for a specified city"; }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "City name, e.g. Beijing"),
                        "country", Map.of("type", "string", "description", "Country code, e.g. CN")
                ),
                "required", List.of("city")
        );
    }

    @Override
    public Mono<McpToolCallResult> execute(Map<String, Object> arguments) {
        String city = (String) arguments.get("city");
        String country = (String) arguments.getOrDefault("country", "");
        final String query = country.isEmpty() ? city : city + "," + country;

        return webClientBuilder.baseUrl(baseUrl).build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + query)
                        .queryParam("format", "j1")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse weather JSON: " + e.getMessage(), e);
                    }
                })
                .map(this::formatResult)
                .onErrorResume(e -> {
                    log.error("Weather query failed for {}", city, e);
                    return Mono.just(McpToolCallResult.builder()
                            .content(List.of(McpToolCallResult.McpContentItem.builder()
                                    .type("text").text("Weather query failed: " + e.getMessage()).build()))
                            .isError(true)
                            .build());
                });
    }

    @SuppressWarnings("unchecked")
    private McpToolCallResult formatResult(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Weather results:\n\n");

        List<Map<String, Object>> current = (List<Map<String, Object>>) data.get("current_condition");
        if (current != null && !current.isEmpty()) {
            Map<String, Object> c = current.get(0);
            sb.append("Temperature: ").append(c.get("temp_C")).append("°C\n");
            sb.append("Feels like: ").append(c.get("FeelsLikeC")).append("°C\n");
            sb.append("Humidity: ").append(c.get("humidity")).append("%\n");
            sb.append("Condition: ").append(getWeatherDesc(c.get("weatherDesc"))).append("\n");
            sb.append("Wind speed: ").append(c.get("windspeedKmph")).append(" km/h\n");
            sb.append("Wind direction: ").append(c.get("winddir16Point")).append("\n");
            sb.append("Visibility: ").append(c.get("visibility")).append(" km\n");
            sb.append("Pressure: ").append(c.get("pressure")).append(" hPa\n");
        }

        List<Map<String, Object>> weather = (List<Map<String, Object>>) data.get("weather");
        if (weather != null && !weather.isEmpty()) {
            sb.append("\nForecast:\n");
            for (int i = 0; i < Math.min(3, weather.size()); i++) {
                Map<String, Object> day = weather.get(i);
                sb.append("  ").append(day.get("date")).append(": ");
                sb.append(day.get("mintempC")).append("°C ~ ");
                sb.append(day.get("maxtempC")).append("°C, ");
                List<Map<String, Object>> hourly = (List<Map<String, Object>>) day.get("hourly");
                if (hourly != null && !hourly.isEmpty()) {
                    sb.append(getWeatherDesc(hourly.get(0).get("weatherDesc")));
                }
                sb.append("\n");
            }
        }

        return McpToolCallResult.builder()
                .content(List.of(McpToolCallResult.McpContentItem.builder()
                        .type("text").text(sb.toString()).build()))
                .isError(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String getWeatherDesc(Object descObj) {
        if (descObj instanceof List && !((List<?>) descObj).isEmpty()) {
            Object first = ((List<?>) descObj).get(0);
            if (first instanceof Map) {
                return ((String) ((Map<String, Object>) first).get("value")).trim();
            }
        }
        return String.valueOf(descObj);
    }
}
