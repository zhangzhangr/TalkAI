package com.talkai.photo.dto;

import lombok.Data;

@Data
public class RepairRequest {
    /** 手动指定保真权重（null=使用AI建议），范围 0.3-0.7 */
    private Double fidelity;
    /** 手动指定是否上色（null=使用AI建议） */
    private Boolean colorize;
    /** 是否增强背景 */
    private Boolean backgroundEnhance;
    /** 是否增强人脸（默认true） */
    private Boolean faceEnhance;
}
