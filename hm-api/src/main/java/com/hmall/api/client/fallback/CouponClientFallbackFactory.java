package com.hmall.api.client.fallback;

import com.hmall.api.client.CouponClient;
import com.hmall.api.dto.CouponConsumeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class CouponClientFallbackFactory implements FallbackFactory<CouponClient> {
    @Override
    public CouponClient create(Throwable cause) {
        return dto -> {
            // 优惠券核销失败不能静默按0优惠放行，否则用户的券会被吞掉却没享受折扣，
            // 这里直接抛出，让下单事务回滚
            log.error("coupon consume failed! code={}", dto == null ? null : dto.getCouponCode(), cause);
            throw new RuntimeException("优惠券服务不可用", cause);
        };
    }
}
