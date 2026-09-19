package com.hmall.seckill.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Schema(description = "创建秒杀活动表单")
public class SeckillActivityFormDTO {
    @Schema(description = "商品id")
    @NotNull(message = "商品id不能为空")
    private Long itemId;

    @Schema(description = "秒杀价（分）")
    @NotNull(message = "秒杀价不能为空")
    @Min(value = 1, message = "秒杀价必须大于0")
    private Integer seckillPrice;

    @Schema(description = "秒杀名额")
    @NotNull(message = "秒杀名额不能为空")
    @Min(value = 1, message = "秒杀名额必须大于0")
    private Integer stockQuota;

    @Schema(description = "开始时间，不传则立即开始")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
