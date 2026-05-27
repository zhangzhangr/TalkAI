package com.talkai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {

    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private String model;
}
