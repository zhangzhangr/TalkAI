package com.talkai.chat.controller;

import com.talkai.chat.dto.ConversationResponse;
import com.talkai.chat.dto.SendMessageRequest;
import com.talkai.chat.dto.UpdateTitleRequest;
import com.talkai.chat.service.ChatService;
import com.talkai.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** SSE streaming: send a message and receive real-time response */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessage(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(userId, request);
    }

    /** List all conversations for current user */
    @GetMapping("/conversations")
    public R<List<ConversationResponse>> listConversations(
            @RequestHeader("X-User-Id") Long userId) {
        return R.ok(chatService.listConversations(userId));
    }

    /** Get a conversation with all messages */
    @GetMapping("/conversations/{id}")
    public R<ConversationResponse> getConversation(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return R.ok(chatService.getConversation(userId, id));
    }

    /** Delete a conversation */
    @DeleteMapping("/conversations/{id}")
    public R<Void> deleteConversation(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        chatService.deleteConversation(userId, id);
        return R.ok();
    }

    /** Update conversation title */
    @PatchMapping("/conversations/{id}/title")
    public R<ConversationResponse> updateTitle(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTitleRequest request) {
        return R.ok(chatService.updateTitle(userId, id, request.getTitle()));
    }
}
