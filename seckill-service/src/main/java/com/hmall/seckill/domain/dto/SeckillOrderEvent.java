package com.hmall.seckill.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 预扣成功后投递的异步下单消息，把落库和调用 item-service 扣真实库存
 * 都挪出秒杀请求的同步路径
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderEvent {
    private Long orderId;
    private Long activityId;
    private Long userId;
    private Long itemId;
    private Integer seckillPrice;
}
