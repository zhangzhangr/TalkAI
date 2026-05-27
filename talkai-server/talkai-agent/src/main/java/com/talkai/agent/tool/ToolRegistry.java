package com.talkai.agent.tool;

import com.talkai.agent.protocol.McpToolDefinition;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    public ToolRegistry(List<ToolDefinition> toolDefinitions) {
        for (ToolDefinition t : toolDefinitions) {
            tools.put(t.getName(), t);
        }
    }

    public List<McpToolDefinition> listTools() {
        return tools.values().stream()
                .map(t -> McpToolDefinition.builder()
                        .name(t.getName())
                        .description(t.getDescription())
                        .inputSchema(t.getInputSchema())
                        .build())
                .toList();
    }

    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
