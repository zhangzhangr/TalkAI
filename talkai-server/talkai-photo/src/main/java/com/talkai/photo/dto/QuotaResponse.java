package com.talkai.photo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuotaResponse {
    /** 免费总次数 */
    private Integer totalFree;
    /** 已使用免费次数 */
    private Integer usedFree;
    /** 剩余免费次数 */
    private Integer remainingFree;
    /** 已购买总次数 */
    private Integer totalPaid;
    /** 已使用付费次数 */
    private Integer usedPaid;
    /** 剩余付费次数 */
    private Integer remainingPaid;
    /** 总剩余次数 */
    private Integer totalRemaining;
}
