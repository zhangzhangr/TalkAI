package com.talkai.agent.protocol;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class McpInitializeResult {
    private String protocolVersion;
    private McpServerCapabilities capabilities;
    private Map<String, Object> serverInfo;
}
