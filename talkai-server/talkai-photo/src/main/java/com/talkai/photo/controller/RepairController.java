package com.talkai.photo.controller;

import com.talkai.common.result.R;
import com.talkai.photo.dto.RepairRequest;
import com.talkai.photo.dto.RepairResponse;
import com.talkai.photo.service.RepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/photo/repair")
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;

    /** 触发修复（阻塞操作放到 boundedElastic 线程池） */
    @PostMapping("/{photoId}")
    public Mono<R<RepairResponse>> repair(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long photoId,
            @RequestBody(required = false) RepairRequest request) {
        if (request == null) {
            request = new RepairRequest();
        }
        final RepairRequest finalRequest = request;
        return Mono.fromCallable(() -> repairService.repair(userId, photoId, finalRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /** 查询修复状态 */
    @GetMapping("/{photoId}/status")
    public Mono<R<RepairResponse>> status(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long photoId) {
        return Mono.fromCallable(() -> repairService.getStatus(userId, photoId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /** Python 修复完成后的回调（白名单，无需 JWT） */
    @PostMapping("/callback")
    public Mono<R<Void>> callback(@RequestBody Map<String, Object> body) {
        return Mono.<Void>fromRunnable(() -> {
            String jobId = (String) body.get("job_id");
            String resultPath = (String) body.getOrDefault("result_path", "");
            String status = (String) body.getOrDefault("status", "COMPLETED");
            String errorMessage = (String) body.getOrDefault("error_message", null);
            log.info("Received repair callback: jobId={}, status={}", jobId, status);
            repairService.handleCallback(jobId, resultPath, status, errorMessage);
        }).subscribeOn(Schedulers.boundedElastic()).map(R::ok);
    }
}
