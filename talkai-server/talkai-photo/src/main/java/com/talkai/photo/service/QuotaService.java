package com.talkai.photo.service;

import com.talkai.common.exception.BusinessException;
import com.talkai.photo.dto.QuotaResponse;
import com.talkai.photo.entity.UserQuota;
import com.talkai.photo.mapper.UserQuotaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UserQuotaMapper userQuotaMapper;

    public UserQuota getOrCreate(Long userId) {
        UserQuota quota = userQuotaMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserQuota>()
                        .eq(UserQuota::getUserId, userId));
        if (quota == null) {
            quota = new UserQuota();
            quota.setUserId(userId);
            quota.setTotalFree(3);
            quota.setUsedFree(0);
            quota.setTotalPaid(0);
            quota.setUsedPaid(0);
            userQuotaMapper.insert(quota);
            log.info("Quota initialized for user: {}", userId);
        }
        return quota;
    }

    @Transactional
    public boolean consumeFree(Long userId) {
        UserQuota quota = getOrCreate(userId);
        if (quota.getUsedFree() < quota.getTotalFree()) {
            quota.setUsedFree(quota.getUsedFree() + 1);
            userQuotaMapper.updateById(quota);
            return true;
        }
        return consumePaid(userId);
    }

    @Transactional
    public boolean consumePaid(Long userId) {
        UserQuota quota = getOrCreate(userId);
        int remainingPaid = quota.getTotalPaid() - quota.getUsedPaid();
        if (remainingPaid > 0) {
            quota.setUsedPaid(quota.getUsedPaid() + 1);
            userQuotaMapper.updateById(quota);
            return true;
        }
        return false;
    }

    public boolean hasQuota(Long userId) {
        UserQuota quota = getOrCreate(userId);
        int freeRemaining = quota.getTotalFree() - quota.getUsedFree();
        int paidRemaining = quota.getTotalPaid() - quota.getUsedPaid();
        return freeRemaining > 0 || paidRemaining > 0;
    }

    public QuotaResponse getQuota(Long userId) {
        UserQuota quota = getOrCreate(userId);
        int remainingFree = quota.getTotalFree() - quota.getUsedFree();
        int remainingPaid = quota.getTotalPaid() - quota.getUsedPaid();
        return QuotaResponse.builder()
                .totalFree(quota.getTotalFree())
                .usedFree(quota.getUsedFree())
                .remainingFree(Math.max(0, remainingFree))
                .totalPaid(quota.getTotalPaid())
                .usedPaid(quota.getUsedPaid())
                .remainingPaid(Math.max(0, remainingPaid))
                .totalRemaining(Math.max(0, remainingFree) + Math.max(0, remainingPaid))
                .build();
    }

    /** 增加付费次数（购买后调用） */
    @Transactional
    public void addPaidCredits(Long userId, int credits) {
        UserQuota quota = getOrCreate(userId);
        quota.setTotalPaid(quota.getTotalPaid() + credits);
        userQuotaMapper.updateById(quota);
        log.info("Added {} paid credits for user: {}", credits, userId);
    }
}
