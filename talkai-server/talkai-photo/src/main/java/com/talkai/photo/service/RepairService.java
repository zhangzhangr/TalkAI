package com.talkai.photo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkai.common.exception.BusinessException;
import com.talkai.photo.dto.DamageAnalysisResponse;
import com.talkai.photo.dto.RepairRequest;
import com.talkai.photo.dto.RepairResponse;
import com.talkai.photo.entity.Photo;
import com.talkai.photo.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairService {

    private final PhotoMapper photoMapper;
    private final PhotoService photoService;
    private final QuotaService quotaService;
    private final ClaudeAnalysisService claudeAnalysisService;
    private final ComparisonService comparisonService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${talkai.photo.python-url:http://localhost:9000}")
    private String pythonUrl;

    /**
     * 完整的修复流程：
     * 1. 检查配额
     * 2. Claude AI 分析破损等级
     * 3. 调用 Python 修复服务
     * 4. 返回当前状态
     */
    @Transactional
    public RepairResponse repair(Long userId, Long photoId, RepairRequest request) {
        Photo photo = photoService.getAndValidate(userId, photoId);

        if (!"UPLOADED".equals(photo.getStatus()) && !"FAILED".equals(photo.getStatus())) {
            throw new BusinessException(400, "该照片状态不允许修复: " + photo.getStatus());
        }

        // 1. 检查配额
        if (!quotaService.hasQuota(userId)) {
            throw new BusinessException(402, "免费次数已用完，请购买付费次数");
        }

        // 2. Claude AI 分析
        photo.setStatus("ANALYZING");
        photoMapper.updateById(photo);

        String imagePath = photoService.getFullPath(photo.getOriginalUrl());
        DamageAnalysisResponse analysis = claudeAnalysisService.analyze(imagePath);

        // 3. 确定修复参数（用户手动覆盖优先）
        Double fidelity = request.getFidelity() != null ? request.getFidelity() : analysis.getSuggestedFidelity();
        Boolean colorize = request.getColorize() != null ? request.getColorize() : analysis.getShouldColorize();
        Boolean bgEnhance = request.getBackgroundEnhance() != null ? request.getBackgroundEnhance() : false;
        Boolean faceEnhance = request.getFaceEnhance() != null ? request.getFaceEnhance() : true;

        photo.setDamageLevel(analysis.getDamageLevel());
        photo.setFidelity(fidelity);
        photo.setColorize(colorize);
        photoMapper.updateById(photo);

        // 4. 调用 Python 修复服务
        try {
            photo.setStatus("REPAIRING");
            photoMapper.updateById(photo);

            Map<String, Object> pythonRequest = new LinkedHashMap<>();
            pythonRequest.put("image_path", imagePath);
            pythonRequest.put("fidelity_weight", fidelity);
            pythonRequest.put("colorize", colorize);
            pythonRequest.put("background_enhance", bgEnhance);
            pythonRequest.put("face_enhance", faceEnhance);
            // Python 完成后的回调 URL（直连，不走网关）
            pythonRequest.put("callback_url", "http://localhost:8089/api/photo/repair/callback");

            @SuppressWarnings("unchecked")
            Map<String, Object> pythonResponse = webClientBuilder.baseUrl(pythonUrl).build()
                    .post()
                    .uri("/api/repair")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(pythonRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (pythonResponse != null && pythonResponse.containsKey("job_id")) {
                // 立即提交 pythonJobId，防止回调先于事务提交到达
                savePythonJobId(photo.getId(), (String) pythonResponse.get("job_id"));
            }

            // 扣减配额
            quotaService.consumeFree(userId);

        } catch (Exception e) {
            log.error("Failed to submit repair job for photo {}: {}", photoId, e.getMessage());
            photo.setStatus("FAILED");
            photo.setErrorMessage("Python 修复服务调用失败: " + e.getMessage());
            photoMapper.updateById(photo);
        }

        return photoService.detail(userId, photoId);
    }

    /** 查询修复状态 */
    public RepairResponse getStatus(Long userId, Long photoId) {
        return photoService.detail(userId, photoId);
    }

    /** Python 修复完成后的回调（成功或失败） */
    @Transactional
    public void handleCallback(String jobId, String resultPath, String status, String errorMessage) {
        Photo photo = photoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Photo>()
                        .eq(Photo::getPythonJobId, jobId));
        if (photo == null) {
            log.warn("Callback for unknown job: {}", jobId);
            return;
        }

        if ("FAILED".equals(status)) {
            photo.setStatus("FAILED");
            photo.setErrorMessage(errorMessage != null ? errorMessage : "Python 修复失败");
            photoMapper.updateById(photo);
            log.info("Photo repair failed: id={}, jobId={}, error={}", photo.getId(), jobId, errorMessage);
            return;
        }

        // 将 Python 输出的绝对路径转为相对存储路径
        String resultFilename = resultPath.substring(resultPath.lastIndexOf('/') + 1);
        photo.setRestoredUrl(resultFilename);
        photo.setStatus("COMPLETED");
        photoMapper.updateById(photo);

        // 异步生成对比图
        try {
            comparisonService.generateComparison(photo);
        } catch (Exception e) {
            log.error("Failed to generate comparison for photo {}: {}", photo.getId(), e.getMessage());
        }

        // 生成引流文案
        try {
            String copyText = claudeAnalysisService.generateCopy(
                    photo.getDamageLevel() != null ? photo.getDamageLevel() : "SLIGHT");
            photo.setCopyText(copyText);
            photoMapper.updateById(photo);
        } catch (Exception e) {
            log.error("Failed to generate copy for photo {}: {}", photo.getId(), e.getMessage());
        }

        log.info("Photo repair completed: id={}, jobId={}", photo.getId(), jobId);
    }

    /** 在独立事务中保存 pythonJobId，确保回调能立即查到该记录 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePythonJobId(Long photoId, String pythonJobId) {
        Photo photo = photoMapper.selectById(photoId);
        if (photo != null) {
            photo.setPythonJobId(pythonJobId);
            photoMapper.updateById(photo);
        }
    }
}
