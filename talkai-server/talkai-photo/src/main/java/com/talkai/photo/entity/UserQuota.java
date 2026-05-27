package com.talkai.photo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_quota")
public class UserQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer totalFree;
    private Integer usedFree;
    private Integer totalPaid;
    private Integer usedPaid;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
