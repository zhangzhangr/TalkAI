package com.talkai.agent.transport;

import lombok.Data;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
public class McpSession {
    private String sessionId;
    private Sinks.Many<String> eventSink;
    private Map<String, Object> clientCapabilities;
    private String protocolVersion;
    private boolean initialized;
    private Instant createdAt;

    public McpSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer(256, false);
        this.createdAt = Instant.now();
    }

    public void complete() {
        eventSink.tryEmitComplete();
    }
}
