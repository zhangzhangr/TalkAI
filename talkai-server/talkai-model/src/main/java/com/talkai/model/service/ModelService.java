package com.talkai.model.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final WebClient.Builder webClientBuilder;

    @Value("${talkai.model.providers.openai.api-key:}")
    private String openaiApiKey;

    @Value("${talkai.model.providers.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${talkai.model.providers.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${talkai.model.providers.deepseek.base-url:https://api.deepseek.com/v1}")
    private String deepseekBaseUrl;

    public Flux<String> chatStream(Map<String, Object> request) {
        String model = (String) request.getOrDefault("model", "deepseek-chat");

        ProviderConfig provider = resolveProvider(model);
        if (provider == null) {
            return Flux.just("data: {\"error\":\"Unsupported model: " + model + "\"}\n\n");
        }

        return webClientBuilder
                .baseUrl(provider.baseUrl)
                .defaultHeader("Authorization", "Bearer " + provider.apiKey)
                .build()
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> log.error("Model API error for model {}: {}", model, e.getMessage()));
    }

    private ProviderConfig resolveProvider(String model) {
        if (model.contains("deepseek")) {
            return new ProviderConfig(deepseekBaseUrl, deepseekApiKey);
        }
        if (model.contains("gpt") || model.contains("openai")) {
            return new ProviderConfig(openaiBaseUrl, openaiApiKey);
        }
        // default to DeepSeek
        return new ProviderConfig(deepseekBaseUrl, deepseekApiKey);
    }

    private record ProviderConfig(String baseUrl, String apiKey) {}
}
