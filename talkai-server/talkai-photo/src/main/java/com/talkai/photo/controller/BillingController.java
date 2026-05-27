package com.talkai.photo.controller;

import com.talkai.common.result.R;
import com.talkai.photo.entity.BillingRecord;
import com.talkai.photo.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/photo/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/list")
    public R<List<BillingRecord>> list(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(billingService.listByUser(userId));
    }

    @GetMapping("/total")
    public R<BigDecimal> total(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(billingService.totalSpent(userId));
    }
}
