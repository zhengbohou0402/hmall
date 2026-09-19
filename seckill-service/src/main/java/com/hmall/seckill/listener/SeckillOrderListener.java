package com.hmall.seckill.listener;

import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.utils.RedisStockUtils;
import com.hmall.seckill.constants.SeckillConstants;
import com.hmall.seckill.domain.dto.SeckillOrderEvent;
import com.hmall.seckill.domain.po.SeckillOrder;
import com.hmall.seckill.service.ISeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀订单异步落库：Redis 是限流闸门（已经保证了不超卖、一人一单），
 * 这里负责把预扣结果持久化，并扣减 item-service 里的真实库存作为最终对账。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillOrderListener {

    private final ISeckillOrderService seckillOrderService;
    private final ItemClient itemClient;
    private final RedisStockUtils redisStockUtils;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = SeckillConstants.SECKILL_ORDER_QUEUE_NAME),
            exchange = @Exchange(name = SeckillConstants.SECKILL_EXCHANGE_NAME, delayed = "false"),
            key = SeckillConstants.SECKILL_ORDER_KEY
    ))
    public void listenSeckillOrder(SeckillOrderEvent event) {
        // 幂等：MQ 可能重投，同一个订单id只处理一次
        if (seckillOrderService.getById(event.getOrderId()) != null) {
            log.info("秒杀订单{}已处理过，跳过", event.getOrderId());
            return;
        }

        SeckillOrder order = new SeckillOrder()
                .setId(event.getOrderId())
                .setActivityId(event.getActivityId())
                .setUserId(event.getUserId())
                .setItemId(event.getItemId())
                .setSeckillPrice(event.getSeckillPrice())
                .setStatus(1);
        seckillOrderService.save(order);

        try {
            itemClient.deductStock(List.of(
                    new OrderDetailDTO().setItemId(event.getItemId()).setNum(1)));
            seckillOrderService.lambdaUpdate()
                    .set(SeckillOrder::getStatus, 2)
                    .eq(SeckillOrder::getId, event.getOrderId())
                    .update();
        } catch (Exception e) {
            // 真实库存扣减失败（例如商品实际库存不足），把 Redis 预扣的名额还回去，
            // 让别的用户还能抢，并把订单标记为失败
            log.error("秒杀订单{}扣减真实库存失败，回滚Redis预扣", event.getOrderId(), e);
            redisStockUtils.restore(
                    SeckillConstants.stockKey(event.getActivityId()),
                    SeckillConstants.userSetKey(event.getActivityId()),
                    1, event.getUserId());
            seckillOrderService.lambdaUpdate()
                    .set(SeckillOrder::getStatus, 3)
                    .eq(SeckillOrder::getId, event.getOrderId())
                    .update();
        }
    }
}
