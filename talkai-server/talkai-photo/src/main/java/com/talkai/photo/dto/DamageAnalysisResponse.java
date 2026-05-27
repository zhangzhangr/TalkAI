package com.talkai.photo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DamageAnalysisResponse {
    /** 破损等级: NONE, SLIGHT, MODERATE, SEVERE */
    private String damageLevel;
    /** AI建议的保真度参数 0.3-0.7 */
    private Double suggestedFidelity;
    /** 是否应开启黑白上色 */
    private Boolean shouldColorize;
    /** AI生成的引流文案（修复完成后返回） */
    private String copyText;
    /** 分析详情（中文描述） */
    private String analysisDetail;
}
