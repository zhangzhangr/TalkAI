package com.talkai.photo.service;

import com.talkai.photo.entity.Photo;
import com.talkai.photo.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final WebClient.Builder webClientBuilder;
    private final PhotoService photoService;
    private final PhotoMapper photoMapper;

    @Value("${talkai.photo.python-url:http://localhost:9000}")
    private String pythonUrl;

    /**
     * 调用 Python 服务合成左右对比图
     */
    public void generateComparison(Photo photo) {
        String originalPath = photoService.getFullPath(photo.getOriginalUrl());
        String restoredPath = photoService.getFullPath(photo.getRestoredUrl());

        try {
            Map<String, Object> request = Map.of(
                    "original_path", originalPath,
                    "restored_path", restoredPath,
                    "layout", "side-by-side"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder.baseUrl(pythonUrl).build()
                    .post()
                    .uri("/api/repair/" + photo.getPythonJobId() + "/comparison")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("comparison_path")) {
                String comparisonPath = (String) response.get("comparison_path");
                String filename = comparisonPath.substring(comparisonPath.lastIndexOf('/') + 1);
                photo.setComparisonUrl(filename);
                photoMapper.updateById(photo);
                log.info("Comparison image generated: {}", filename);
            }
        } catch (Exception e) {
            log.error("Comparison generation failed for photo {}: {}", photo.getId(), e.getMessage());
        }
    }
}
