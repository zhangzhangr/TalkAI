package com.talkai.photo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.talkai.photo.entity.BillingRecord;
import com.talkai.photo.mapper.BillingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillingRecordMapper billingRecordMapper;

    /** 定价表 */
    public static final BigDecimal PRICE_BASIC = new BigDecimal("29.9");
    public static final BigDecimal PRICE_COLORIZE = new BigDecimal("49.9");
    public static final BigDecimal PRICE_PREMIUM = new BigDecimal("89.9");
    public static final BigDecimal PRICE_BATCH = new BigDecimal("19.9");

    /** 创建待支付订单 */
    @Transactional
    public BillingRecord createOrder(Long userId, Long photoId, BigDecimal amount, int credits) {
        BillingRecord record = new BillingRecord();
        record.setUserId(userId);
        record.setPhotoId(photoId);
        record.setAmount(amount);
        record.setCredits(credits);
        record.setStatus("PENDING");
        record.setTransactionId(UUID.randomUUID().toString().replace("-", ""));
        billingRecordMapper.insert(record);
        log.info("Billing order created: id={}, userId={}, amount={}", record.getId(), userId, amount);
        return record;
    }

    /** 标记支付完成 */
    @Transactional
    public void markPaid(String transactionId) {
        BillingRecord record = billingRecordMapper.selectOne(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getTransactionId, transactionId));
        if (record != null) {
            record.setStatus("PAID");
            billingRecordMapper.updateById(record);
            log.info("Payment marked as paid: transactionId={}", transactionId);
        }
    }

    public List<BillingRecord> listByUser(Long userId) {
        return billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getUserId, userId)
                        .orderByDesc(BillingRecord::getCreateTime));
    }

    /** 统计用户总消费 */
    public BigDecimal totalSpent(Long userId) {
        List<BillingRecord> records = billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getUserId, userId)
                        .eq(BillingRecord::getStatus, "PAID"));
        return records.stream()
                .map(BillingRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
