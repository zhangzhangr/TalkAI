package com.talkai.photo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("billing_record")
public class BillingRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long photoId;
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionId;
    private String status;
    private Integer credits;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
