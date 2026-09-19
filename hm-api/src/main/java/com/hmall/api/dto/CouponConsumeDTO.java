package com.hmall.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "优惠券核销请求")
@Data
@Accessors(chain = true)
public class CouponConsumeDTO {
    @Schema(description = "优惠码")
    private String couponCode;
    @Schema(description = "下单用户id，Feign调用间不依赖登录态透传，由调用方显式传入")
    private Long userId;
    @Schema(description = "订单原始金额(分)")
    private Integer orderAmount;
    @Schema(description = "核销关联的订单id")
    private Long orderId;
}
