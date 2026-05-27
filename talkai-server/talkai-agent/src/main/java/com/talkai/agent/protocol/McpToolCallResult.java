package com.talkai.agent.protocol;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class McpToolCallResult {
    private List<McpContentItem> content;
    private boolean isError;

    @Data
    @Builder
    public static class McpContentItem {
        private String type;
        private String text;
    }
}
