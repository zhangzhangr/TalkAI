package com.talkai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private Long id;
    private String title;
    private String model;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MessageResponse> messages;
}
