package com.hmall.api.client.fallback;

import com.hmall.api.client.PayClient;
import com.hmall.api.dto.PayOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class PayClientFallBackFactory implements FallbackFactory<PayClient> {
    @Override
    public PayClient create(Throwable cause) {
        return new PayClient() {
            @Override
            public PayOrderDTO queryPayOrderByBizOrderNo(Long id) {
                return null;
            }

            @Override
            public void updatePayOrderStatusByBizOrderNo(Long orderId, Integer status) {
                try {
                        log.warn("Feign 调用失败， orderId = {}, status = {}", orderId, status);
                    } catch (Exception e) {
                        log.error("FallBack出错: {}", e.getMessage());
                }
            }
        };
    }
}
