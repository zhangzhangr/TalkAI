package com.talkai.photo.controller;

import com.talkai.common.result.R;
import com.talkai.photo.dto.QuotaResponse;
import com.talkai.photo.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/photo/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @GetMapping
    public R<QuotaResponse> getQuota(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(quotaService.getQuota(userId));
    }

    @GetMapping("/remaining")
    public R<Integer> remaining(@RequestHeader("X-User-Id") Long userId) {
        QuotaResponse q = quotaService.getQuota(userId);
        return R.ok(q.getTotalRemaining());
    }
}
