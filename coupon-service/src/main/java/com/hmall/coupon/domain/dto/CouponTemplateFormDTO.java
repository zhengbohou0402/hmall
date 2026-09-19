package com.hmall.coupon.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Schema(description = "创建优惠券模板表单")
public class CouponTemplateFormDTO {
    @Schema(description = "券名称")
    @NotNull(message = "券名称不能为空")
    private String name;

    @Schema(description = "优惠类型 1满减 2折扣")
    @NotNull(message = "优惠类型不能为空")
    private Integer discountType;

    @Schema(description = "满减为减免金额(分)，折扣为折扣率(85=8.5折)")
    @NotNull(message = "优惠值不能为空")
    @Min(value = 1, message = "优惠值必须大于0")
    private Integer discountValue;

    @Schema(description = "使用门槛金额(分)")
    private Integer thresholdAmount = 0;

    @Schema(description = "发放总量")
    @NotNull(message = "发放总量不能为空")
    @Min(value = 1, message = "发放总量必须大于0")
    private Integer totalQuantity;

    @Schema(description = "每人限领数量，默认1")
    private Integer perUserLimit = 1;

    private LocalDateTime validStart;
    private LocalDateTime validEnd;
}
