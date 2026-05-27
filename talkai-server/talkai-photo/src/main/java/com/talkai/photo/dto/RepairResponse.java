package com.talkai.photo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RepairResponse {
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
