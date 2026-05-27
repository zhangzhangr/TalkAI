package com.talkai.agent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {
    private String jsonrpc;
    private Object id;
    private Object result;
    private JsonRpcError error;
}
