package com.hmall.api.client;

import com.hmall.api.client.fallback.CouponClientFallbackFactory;
import com.hmall.api.dto.CouponConsumeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "coupon-service", fallbackFactory = CouponClientFallbackFactory.class)
public interface CouponClient {

    /**
     * 校验并核销优惠券，返回优惠金额（分）。校验不通过时抛业务异常。
     */
    @PostMapping("/coupons/consume")
    Integer validateAndConsume(@RequestBody CouponConsumeDTO dto);
}
