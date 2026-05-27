package com.talkai.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkai.chat.dto.ConversationResponse;
import com.talkai.chat.dto.MessageResponse;
import com.talkai.chat.dto.SendMessageRequest;
import com.talkai.chat.entity.Conversation;
import com.talkai.chat.entity.Message;
import com.talkai.chat.mapper.ConversationMapper;
import com.talkai.chat.mapper.MessageMapper;
import com.talkai.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${talkai.model.url:http://talkai-model}")
    private String modelServiceUrl;

    @Value("${talkai.agent.url:http://talkai-agent}")
    private String agentServiceUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile List<Map<String, Object>> cachedToolDefs = List.of();

    @PostConstruct
    void init() {
        refreshToolDefs();
    }

    private void refreshToolDefs() {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = webClientBuilder.baseUrl(agentServiceUrl).build()
                    .get()
                    .uri("/api/agent/tools")
                    .retrieve()
                    .bodyToMono(List.class)
                    .map(list -> {
                        List<Map<String, Object>> openaiTools = new ArrayList<>();
                        for (Object item : list) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> def = (Map<String, Object>) item;
                            openaiTools.add(Map.of(
                                    "type", "function",
                                    "function", Map.of(
                                            "name", def.get("name"),
                                            "description", def.get("description"),
                                            "parameters", def.getOrDefault("inputSchema", Map.of())
                                    )
                            ));
                        }
                        return openaiTools;
                    })
                    .block();
            if (tools != null) {
                cachedToolDefs = tools;
                log.info("Loaded {} tool definitions from agent", tools.size());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch tool definitions from agent: {}", e.getMessage());
        }
    }

    // ==================== Conversation Management ====================

    public List<ConversationResponse> listConversations(Long userId) {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getUpdateTime));

        return conversations.stream().map(conv -> ConversationResponse.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .model(conv.getModel())
                .messageCount(conv.getMessageCount())
                .createTime(conv.getCreateTime())
                .updateTime(conv.getUpdateTime())
                .build()).collect(Collectors.toList());
    }

    public ConversationResponse getConversation(Long userId, Long conversationId) {
        Conversation conversation = getAndValidateConversation(userId, conversationId);
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime));

        List<MessageResponse> messageResponses = messages.stream().map(msg -> MessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .role(msg.getRole())
                .content(msg.getContent())
                .tokenCount(msg.getTokenCount())
                .createTime(msg.getCreateTime())
                .build()).collect(Collectors.toList());

        return ConversationResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .model(conversation.getModel())
                .messageCount(conversation.getMessageCount())
                .createTime(conversation.getCreateTime())
                .updateTime(conversation.getUpdateTime())
                .messages(messageResponses)
                .build();
    }

    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = getAndValidateConversation(userId, conversationId);
        conversationMapper.deleteById(conversationId);
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId));
        log.info("Conversation deleted: id={}, userId={}", conversationId, userId);
    }

    @Transactional
    public ConversationResponse updateTitle(Long userId, Long conversationId, String title) {
        Conversation conversation = getAndValidateConversation(userId, conversationId);
        conversation.setTitle(title);
        conversationMapper.updateById(conversation);
        return getConversation(userId, conversationId);
    }

    // ==================== Send Message with SSE Streaming ====================

    @Transactional
    public Flux<ServerSentEvent<String>> sendMessage(Long userId, SendMessageRequest request) {
        Conversation conversation = getOrCreateConversation(userId, request);
        Long conversationId = conversation.getId();

        // save user message
        Message userMessage = new Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(request.getContent());
        userMessage.setTokenCount(0);
        messageMapper.insert(userMessage);

        // update conversation
        conversation.setMessageCount(conversation.getMessageCount() + 1);
        conversation.setUpdateTime(LocalDateTime.now());
        if (conversation.getMessageCount() == 1 && request.getContent().length() > 0) {
            String title = request.getContent().length() > 30
                    ? request.getContent().substring(0, 30) + "..."
                    : request.getContent();
            conversation.setTitle(title);
        }
        conversationMapper.updateById(conversation);

        // build messages for LLM
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime));
        List<Map<String, Object>> llmMessages = history.stream()
                .<Map<String, Object>>map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
                .collect(Collectors.toList());

        // prepend system prompt if set
        if (conversation.getSystemPrompt() != null && !conversation.getSystemPrompt().isBlank()) {
            llmMessages.add(0, Map.of("role", "system", "content", conversation.getSystemPrompt()));
        }

        String model = request.getModel() != null ? request.getModel() : conversation.getModel();

        return processWithTools(conversationId, model, llmMessages, 0);
    }

    /** Recursively process LLM responses, executing tool calls until final text response */
    private Flux<ServerSentEvent<String>> processWithTools(
            Long conversationId, String model, List<Map<String, Object>> messages, int depth) {

        if (depth > 5) {
            return Flux.just(sseEvent("data", "{\"error\":\"Tool call depth exceeded\"}"));
        }

        List<Map<String, Object>> toolDefs = fetchToolDefinitions();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        if (!toolDefs.isEmpty()) {
            requestBody.put("tools", toolDefs);
            requestBody.put("tool_choice", "auto");
        }

        return webClientBuilder.baseUrl(modelServiceUrl).build()
                .post()
                .uri("/api/model/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .collectList()
                .flatMapMany(chunks -> {
                    String fullText = extractFullContent(chunks);
                    List<ToolCall> toolCalls = extractToolCalls(chunks);

                    if (!toolCalls.isEmpty()) {
                        // add assistant message with tool calls
                        messages.add(Map.of("role", "assistant", "content", fullText.isEmpty() ? "" : fullText,
                                "tool_calls", toolCalls.stream().map(tc -> Map.of(
                                        "id", tc.id,
                                        "type", "function",
                                        "function", Map.of("name", tc.name, "arguments", tc.arguments)
                                )).collect(Collectors.toList())));

                        // execute each tool and add results
                        return Flux.fromIterable(toolCalls)
                                .flatMap(tc -> executeTool(tc.name, tc.arguments)
                                        .map(result -> Map.<String, Object>of(
                                                "role", "tool",
                                                "tool_call_id", tc.id,
                                                "content", result
                                        )))
                                .collectList()
                                .flatMapMany(toolResults -> {
                                    messages.addAll(toolResults);
                                    return processWithTools(conversationId, model, messages, depth + 1);
                                });
                    }

                    // no tool calls — stream text response to user and save
                    StringBuilder fullResponse = new StringBuilder();
                    return Flux.fromIterable(chunks)
                            .doOnNext(chunk -> {
                                String content = extractContent(chunk);
                                if (!content.isEmpty()) fullResponse.append(content);
                            })
                            .doOnComplete(() -> saveAssistantMessage(conversationId, fullResponse.toString(), model))
                            .map(chunk -> ServerSentEvent.<String>builder()
                                    .data(chunk).build())
                            .concatWith(Flux.just(ServerSentEvent.<String>builder()
                                    .event("done").data("[DONE]").build()));
                });
    }

    /** Fetch tool definitions from agent service with caching */
    private List<Map<String, Object>> fetchToolDefinitions() {
        return cachedToolDefs;
    }

    /** Execute a tool via the agent service */
    @SuppressWarnings("unchecked")
    private Mono<String> executeTool(String name, String arguments) {
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(arguments, Map.class);
        } catch (JsonProcessingException e) {
            parsed = Map.of();
        }
        Map<String, Object> body = Map.of("name", name, "arguments", parsed);

        return webClientBuilder.baseUrl(agentServiceUrl).build()
                .post()
                .uri("/api/agent/tool-call")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(result -> {
                    Object content = result.get("content");
                    if (content instanceof List && !((List<?>) content).isEmpty()) {
                        Object first = ((List<?>) content).get(0);
                        if (first instanceof Map) {
                            return (String) ((Map<String, Object>) first).getOrDefault("text", "");
                        }
                    }
                    return "";
                })
                .onErrorResume(e -> {
                    log.error("Tool execution failed: {}", e.getMessage());
                    return Mono.just("Tool execution error: " + e.getMessage());
                });
    }

    private ServerSentEvent<String> sseEvent(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    /** Extract full text content from all chunks */
    private String extractFullContent(List<String> chunks) {
        StringBuilder sb = new StringBuilder();
        for (String chunk : chunks) {
            sb.append(extractContent(chunk));
        }
        return sb.toString();
    }

    /** Accumulate tool calls from streaming SSE chunks */
    private List<ToolCall> extractToolCalls(List<String> chunks) {
        Map<Integer, ToolCall> toolCalls = new LinkedHashMap<>();
        for (String chunk : chunks) {
            try {
                String json = chunk.strip();
                if (json.startsWith("data:")) {
                    json = json.substring(5).stripLeading();
                }
                if (json.isEmpty() || "[DONE]".equals(json)) continue;

                JsonNode root = objectMapper.readTree(json);
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode delta = choices.get(0).get("delta");
                    if (delta != null) {
                        JsonNode tcNode = delta.get("tool_calls");
                        if (tcNode != null && tcNode.isArray()) {
                            for (JsonNode tc : tcNode) {
                                int idx = tc.get("index").asInt();
                                ToolCall call = toolCalls.computeIfAbsent(idx, k -> new ToolCall());
                                if (tc.has("id") && !tc.get("id").isNull()) {
                                    call.id = tc.get("id").asText();
                                }
                                JsonNode func = tc.get("function");
                                if (func != null) {
                                    if (func.has("name") && !func.get("name").isNull()) {
                                        call.name = func.get("name").asText();
                                    }
                                    if (func.has("arguments") && !func.get("arguments").isNull()) {
                                        call.arguments += func.get("arguments").asText();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(toolCalls.values());
    }

    private static class ToolCall {
        String id = "";
        String name = "";
        String arguments = "";
    }

    private String extractContent(String chunk) {
        try {
            // strip optional SSE "data:" prefix (with or without space)
            String json = chunk.strip();
            if (json.startsWith("data:")) {
                json = json.substring(5).stripLeading();
            }
            if (json.isEmpty() || "[DONE]".equals(json)) {
                return "";
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull()) {
                        return content.asText();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void saveAssistantMessage(Long conversationId, String content, String model) {
        if (content.isEmpty()) return;
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(content);
        assistantMsg.setTokenCount(0);
        messageMapper.insert(assistantMsg);

        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setMessageCount(conv.getMessageCount() + 1);
            conv.setUpdateTime(LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
    }

    // ==================== Helper Methods ====================

    private Conversation getAndValidateConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return conversation;
    }

    private Conversation getOrCreateConversation(Long userId, SendMessageRequest request) {
        if (request.getConversationId() != null) {
            return getAndValidateConversation(userId, request.getConversationId());
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setModel(request.getModel() != null ? request.getModel() : "deepseek-chat");
        conversation.setMessageCount(0);
        conversationMapper.insert(conversation);
        log.info("New conversation created: id={}, userId={}", conversation.getId(), userId);
        return conversation;
    }
}
