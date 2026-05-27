package com.talkai.agent.protocol;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpServerCapabilities {
    private ToolsCapability tools;

    @Data
    @Builder
    public static class ToolsCapability {
        private boolean listChanged;
    }
}
