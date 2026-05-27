package com.talkai.agent.tool;

import com.talkai.agent.protocol.McpToolCallResult;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ToolDefinition {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    Mono<McpToolCallResult> execute(Map<String, Object> arguments);
}
