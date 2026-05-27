package com.talkai.photo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkai.common.exception.BusinessException;
import com.talkai.photo.dto.DamageAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeAnalysisService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${talkai.model.url:http://localhost:8083}")
    private String modelServiceUrl;

    // 修复参数规则
    private static final double SLIGHT_FIDELITY = 0.7;
    private static final double MODERATE_FIDELITY = 0.5;
    private static final double SEVERE_FIDELITY = 0.3;

    /**
     * 分析照片破损等级，返回修复参数建议。
     * 先用 Java 做基本图像特征提取，再发送给 LLM 做智能判断。
     */
    public DamageAnalysisResponse analyze(String imagePath) {
        // 1. 提取图像基本特征
        Map<String, Object> imageFeatures = extractImageFeatures(imagePath);

        // 2. 构建分析请求
        DamageAnalysisResponse aiResult = callModelForAnalysis(imageFeatures);

        // 3. 应用修复参数规则进行校验
        return validateAndApplyRules(aiResult);
    }

    /** 生成引流文案 */
    public String generateCopy(String damageLevel) {
        String prompt = buildCopyPrompt(damageLevel);
        return callModelForCopy(prompt);
    }

    // ---------- private methods ----------

    private Map<String, Object> extractImageFeatures(String imagePath) {
        Map<String, Object> features = new LinkedHashMap<>();
        File file = new File(imagePath);
        features.put("fileName", file.getName());
        features.put("fileSizeKB", file.length() / 1024);

        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                features.put("error", "无法解析图片格式");
                return features;
            }
            features.put("width", img.getWidth());
            features.put("height", img.getHeight());
            features.put("resolution", img.getWidth() + "x" + img.getHeight());

            // 判断是否为黑白照片（采样分析）
            boolean isBlackAndWhite = detectBlackAndWhite(img);
            features.put("isBlackAndWhite", isBlackAndWhite);

            // 检测分辨率是否过低（模糊指标）
            long pixels = (long) img.getWidth() * img.getHeight();
            features.put("totalPixels", pixels);
            boolean isLowRes = pixels < 640L * 480L;
            features.put("isLowResolution", isLowRes);

            // 文件大小过小通常是压缩严重的旧照片
            boolean isHighlyCompressed = file.length() < 50 * 1024 && pixels > 800L * 600L;
            features.put("isHighlyCompressed", isHighlyCompressed);

        } catch (IOException e) {
            features.put("error", "读取图片失败: " + e.getMessage());
        }

        return features;
    }

    /** 简单采样判断是否为黑白图像 */
    private boolean detectBlackAndWhite(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int sampleStep = Math.max(1, Math.min(width, height) / 50);
        int grayCount = 0;
        int totalSamples = 0;

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // 如果 RGB 三个通道差异很小，认为是灰度像素
                if (Math.abs(r - g) < 15 && Math.abs(g - b) < 15 && Math.abs(r - b) < 15) {
                    grayCount++;
                }
                totalSamples++;
            }
        }

        // 如果超过 90% 的像素是灰度，判定为黑白照片
        return totalSamples > 0 && (double) grayCount / totalSamples > 0.9;
    }

    /** 调用 LLM 做破损分析 */
    private DamageAnalysisResponse callModelForAnalysis(Map<String, Object> features) {
        try {
            String featuresJson = objectMapper.writeValueAsString(features);

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content",
                    "你是一位专业的照片修复专家。请根据照片的特征数据，判断照片的破损等级。" +
                    "你必须只返回 JSON 格式，不要包含其他文字。JSON 格式如下：" +
                    "{\"damageLevel\":\"SLIGHT/MODERATE/SEVERE\",\"confidence\":0.0-1.0,\"analysisDetail\":\"简短的中文分析\"}" +
                    "破损等级判断标准：" +
                    "- SLIGHT(轻微): 分辨率正常(>1024x768)，文件大小正常，非黑白，轻微压缩" +
                    "- MODERATE(中度): 分辨率偏低(640x480~1024x768)，有明显压缩，或为黑白老照片" +
                    "- SEVERE(重度): 分辨率极低(<640x480)，高度压缩，或是黑白+低分辨率" +
                    "如果 isBlackAndWhite 为 true，则 shouldColorize 为 true。"));

            messages.add(Map.of("role", "user", "content",
                    "请分析以下照片特征数据，返回破损等级评估：\n" + featuresJson));

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", messages);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.3);

            String response = webClientBuilder.baseUrl(modelServiceUrl).build()
                    .post()
                    .uri("/api/model/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .collectList()
                    .map(chunks -> {
                        StringBuilder sb = new StringBuilder();
                        for (String chunk : chunks) {
                            sb.append(extractContent(chunk));
                        }
                        return sb.toString();
                    })
                    .block();

            if (response != null && !response.isEmpty()) {
                return parseAnalysisResponse(response, features);
            }

        } catch (Exception e) {
            log.warn("AI analysis failed, using heuristic fallback: {}", e.getMessage());
        }

        return heuristicAnalysis(features);
    }

    /** 基于规则的启发式分析（LLM 不可用时的降级方案） */
    private DamageAnalysisResponse heuristicAnalysis(Map<String, Object> features) {
        String damageLevel;
        double fidelity;
        boolean shouldColorize = Boolean.TRUE.equals(features.get("isBlackAndWhite"));
        boolean lowRes = Boolean.TRUE.equals(features.get("isLowResolution"));
        boolean compressed = Boolean.TRUE.equals(features.get("isHighlyCompressed"));

        if (lowRes && compressed) {
            damageLevel = "SEVERE";
            fidelity = SEVERE_FIDELITY;
        } else if (lowRes || compressed || shouldColorize) {
            damageLevel = "MODERATE";
            fidelity = MODERATE_FIDELITY;
        } else {
            damageLevel = "SLIGHT";
            fidelity = SLIGHT_FIDELITY;
        }

        return DamageAnalysisResponse.builder()
                .damageLevel(damageLevel)
                .suggestedFidelity(fidelity)
                .shouldColorize(shouldColorize)
                .analysisDetail("启发式分析: 分辨率" + features.get("resolution")
                        + (shouldColorize ? ", 黑白照片" : "")
                        + (lowRes ? ", 低分辨率" : "")
                        + (compressed ? ", 高压缩" : ""))
                .build();
    }

    private DamageAnalysisResponse parseAnalysisResponse(String response, Map<String, Object> features) {
        try {
            // 尝试从响应中提取 JSON
            String json = response.trim();
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }
            JsonNode root = objectMapper.readTree(json);

            String damageLevel = root.has("damageLevel") ? root.get("damageLevel").asText().toUpperCase() : "SLIGHT";
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : 0.5;
            String detail = root.has("analysisDetail") ? root.get("analysisDetail").asText() : "";

            // 只接受合法的破损等级
            if (!List.of("SLIGHT", "MODERATE", "SEVERE").contains(damageLevel)) {
                damageLevel = "SLIGHT";
            }

            boolean shouldColorize = Boolean.TRUE.equals(features.get("isBlackAndWhite"));
            double fidelity = switch (damageLevel) {
                case "SEVERE" -> SEVERE_FIDELITY;
                case "MODERATE" -> MODERATE_FIDELITY;
                default -> SLIGHT_FIDELITY;
            };

            return DamageAnalysisResponse.builder()
                    .damageLevel(damageLevel)
                    .suggestedFidelity(fidelity)
                    .shouldColorize(shouldColorize)
                    .analysisDetail(detail)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse AI analysis response: {}", e.getMessage());
            return heuristicAnalysis(features);
        }
    }

    /** 校验并应用修复参数规则 */
    private DamageAnalysisResponse validateAndApplyRules(DamageAnalysisResponse response) {
        // 确保保真度在合法范围
        double fidelity = response.getSuggestedFidelity();
        if (fidelity < 0.3) fidelity = 0.3;
        if (fidelity > 0.7) fidelity = 0.7;
        response.setSuggestedFidelity(fidelity);

        // 确认破损等级对应的保真度
        String level = response.getDamageLevel();
        double expectedFidelity = switch (level) {
            case "SEVERE" -> SEVERE_FIDELITY;
            case "MODERATE" -> MODERATE_FIDELITY;
            default -> SLIGHT_FIDELITY;
        };
        response.setSuggestedFidelity(expectedFidelity);

        return response;
    }

    /** 调用 LLM 生成引流文案 */
    private String callModelForCopy(String damageLevel) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content",
                    "你是一位社交媒体营销专家。请为一张" + damageLevel + "破损的老照片修复效果生成3条中文引流文案，" +
                    "分别适配抖音、小红书、闲鱼。每条不超过50字，带emoji，适合做短视频/图文标题。" +
                    "请以 JSON 数组格式返回，如 [\"文案1\",\"文案2\",\"文案3\"]，不要包含其他文字。"));

            messages.add(Map.of("role", "user", "content",
                    "请为老照片修复服务生成3条引流文案（破损等级: " + damageLevel + ")"));

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", messages);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.8);

            String response = webClientBuilder.baseUrl(modelServiceUrl).build()
                    .post()
                    .uri("/api/model/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .collectList()
                    .map(chunks -> {
                        StringBuilder sb = new StringBuilder();
                        for (String chunk : chunks) {
                            sb.append(extractContent(chunk));
                        }
                        return sb.toString();
                    })
                    .block();

            if (response != null && !response.isEmpty()) {
                // 尝试解析 JSON 数组
                return parseCopyResponse(response);
            }

        } catch (Exception e) {
            log.warn("AI copy generation failed: {}", e.getMessage());
        }

        return "老照片焕新，AI智能修复，留住每一段珍贵回忆";
    }

    private String parseCopyResponse(String response) {
        try {
            String json = response.trim();
            int bracketStart = json.indexOf('[');
            int bracketEnd = json.lastIndexOf(']');
            if (bracketStart >= 0 && bracketEnd > bracketStart) {
                json = json.substring(bracketStart, bracketEnd + 1);
                return json;
            }
        } catch (Exception ignored) {
        }
        return "[\"" + response.trim().replace("\"", "'").replace("\n", "\\n") + "\"]";
    }

    private String buildCopyPrompt(String damageLevel) {
        String levelDesc = switch (damageLevel) {
            case "SEVERE" -> "重度破损";
            case "MODERATE" -> "中度老化";
            default -> "轻微泛黄";
        };
        return "请为一张" + levelDesc + "老照片修复效果，生成3条引流文案（抖音、小红书、闲鱼），每条不超过50字。返回JSON数组。";
    }

    /** 从 SSE chunk 中提取 content */
    private String extractContent(String chunk) {
        try {
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
}
