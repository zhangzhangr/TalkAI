package com.talkai.agent.protocol;

import lombok.Data;

import java.util.Map;

@Data
public class JsonRpcRequest {
    private String jsonrpc;
    private Object id;
    private String method;
    private Map<String, Object> params;
}
