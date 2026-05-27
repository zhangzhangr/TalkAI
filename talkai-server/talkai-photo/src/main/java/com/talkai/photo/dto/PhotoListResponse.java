package com.talkai.photo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PhotoListResponse {
    private Long id;
    private String originalUrl;
    private String restoredUrl;
    private String status;
    private String damageLevel;
    private LocalDateTime createTime;
}
