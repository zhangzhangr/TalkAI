package com.talkai.agent.controller;

import com.talkai.agent.protocol.JsonRpcRequest;
import com.talkai.agent.protocol.McpToolCallResult;
import com.talkai.agent.service.McpService;
import com.talkai.agent.tool.ToolRegistry;
import com.talkai.agent.transport.McpSession;
import com.talkai.agent.transport.McpSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class McpController {

    private final McpSessionManager sessionManager;
    private final McpService mcpService;
    private final ToolRegistry toolRegistry;

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> connectSse() {
        McpSession session = sessionManager.createSession();
        String endpoint = "/api/agent/message?sessionId=" + session.getSessionId();

        return Flux.concat(
                Flux.just(ServerSentEvent.<String>builder()
                        .event("endpoint")
                        .data(endpoint)
                        .build()),
                session.getEventSink().asFlux()
                        .map(data -> ServerSentEvent.<String>builder()
                                .event("message")
                                .data(data)
                                .build())
        ).doOnCancel(() -> sessionManager.removeSession(session.getSessionId()))
         .doOnComplete(() -> sessionManager.removeSession(session.getSessionId()));
    }

    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> handleMessage(
            @RequestParam String sessionId,
            @RequestBody JsonRpcRequest request) {
        McpSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return mcpService.handleRequest(request, session)
                .thenReturn(ResponseEntity.accepted().build());
    }

    /** List available tools (simple REST, no MCP session needed) */
    @GetMapping("/tools")
    public Mono<ResponseEntity<?>> listTools() {
        return Mono.just(ResponseEntity.ok(toolRegistry.listTools()));
    }

    /** Execute a tool directly (simple REST, no MCP session needed) */
    @PostMapping("/tool-call")
    public Mono<ResponseEntity<McpToolCallResult>> toolCall(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) body.getOrDefault("arguments", Map.of());

        return toolRegistry.getTool(name)
                .map(tool -> tool.execute(arguments)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.internalServerError().build()))
                .orElseGet(() -> Mono.just(ResponseEntity.notFound().build()));
    }
}
