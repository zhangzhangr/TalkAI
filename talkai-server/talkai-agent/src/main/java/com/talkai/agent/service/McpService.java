package com.talkai.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkai.agent.protocol.*;
import com.talkai.agent.tool.ToolRegistry;
import com.talkai.agent.transport.McpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpService {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    public Mono<Void> handleRequest(JsonRpcRequest request, McpSession session) {
        if (!"2.0".equals(request.getJsonrpc())) {
            sendError(session, request.getId(), INVALID_REQUEST, "Invalid JSON-RPC version");
            return Mono.empty();
        }

        return switch (request.getMethod()) {
            case "initialize" -> handleInitialize(request, session);
            case "tools/list" -> handleToolsList(request, session);
            case "tools/call" -> handleToolsCall(request, session);
            case "notifications/initialized" -> handleInitialized(session);
            default -> {
                sendError(session, request.getId(), METHOD_NOT_FOUND,
                        "Method not found: " + request.getMethod());
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleInitialize(JsonRpcRequest req, McpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = req.getParams();
        if (params != null && params.containsKey("protocolVersion")) {
            session.setProtocolVersion((String) params.get("protocolVersion"));
        }
        if (params != null && params.containsKey("capabilities")) {
            session.setClientCapabilities((Map<String, Object>) params.get("capabilities"));
        }

        McpServerCapabilities.ToolsCapability toolsCap = McpServerCapabilities.ToolsCapability.builder()
                .listChanged(false).build();
        McpServerCapabilities capabilities = McpServerCapabilities.builder()
                .tools(toolsCap).build();

        Map<String, Object> serverInfo = Map.of(
                "name", "TalkAI MCP Server",
                "version", "1.0.0"
        );

        McpInitializeResult result = McpInitializeResult.builder()
                .protocolVersion("2024-11-05")
                .capabilities(capabilities)
                .serverInfo(serverInfo)
                .build();

        sendSuccess(session, req.getId(), result);
        return Mono.empty();
    }

    private Mono<Void> handleInitialized(McpSession session) {
        session.setInitialized(true);
        log.info("MCP session {} initialized", session.getSessionId());
        return Mono.empty();
    }

    private Mono<Void> handleToolsList(JsonRpcRequest req, McpSession session) {
        List<McpToolDefinition> tools = toolRegistry.listTools();
        sendSuccess(session, req.getId(), Map.of("tools", tools));
        return Mono.empty();
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> handleToolsCall(JsonRpcRequest req, McpSession session) {
        Map<String, Object> params = req.getParams();
        if (params == null || !params.containsKey("name")) {
            sendError(session, req.getId(), INVALID_PARAMS, "Missing tool name");
            return Mono.empty();
        }

        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        return toolRegistry.getTool(toolName)
                .map(tool -> tool.execute(arguments)
                        .doOnNext(result -> sendSuccess(session, req.getId(), result))
                        .doOnError(e -> sendError(session, req.getId(), INTERNAL_ERROR, e.getMessage()))
                        .then())
                .orElseGet(() -> {
                    sendError(session, req.getId(), INVALID_PARAMS, "Unknown tool: " + toolName);
                    return Mono.empty();
                });
    }

    private void sendSuccess(McpSession session, Object id, Object result) {
        JsonRpcResponse resp = new JsonRpcResponse();
        resp.setJsonrpc("2.0");
        resp.setId(id);
        resp.setResult(result);
        pushToSink(session, resp);
    }

    private void sendError(McpSession session, Object id, int code, String message) {
        JsonRpcResponse resp = new JsonRpcResponse();
        resp.setJsonrpc("2.0");
        resp.setId(id);
        resp.setError(new JsonRpcError(code, message, null));
        pushToSink(session, resp);
    }

    private void pushToSink(McpSession session, JsonRpcResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            Sinks.EmitResult result = session.getEventSink().tryEmitNext(json);
            if (result.isFailure()) {
                log.warn("Failed to emit SSE for session {}: {}", session.getSessionId(), result);
            }
        } catch (Exception e) {
            log.error("Failed to serialize JSON-RPC response", e);
        }
    }
}
