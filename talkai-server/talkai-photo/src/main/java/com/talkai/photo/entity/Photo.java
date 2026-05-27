package com.talkai.photo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("photo_restoration")
public class Photo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String originalUrl;
    private String restoredUrl;
    private String comparisonUrl;
    private String status;
    private String damageLevel;
    private Double fidelity;
    private Boolean colorize;
    private String copyText;
    private String errorMessage;
    private String pythonJobId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
