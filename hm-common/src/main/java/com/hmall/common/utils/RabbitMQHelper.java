package com.hmall.common.utils;

import cn.hutool.core.lang.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMQHelper {

    private final RabbitTemplate rabbitTemplate;

    public void sendMessage(String exchange, String routingKey, Object msg){
        log.debug("准备发送消息，exchange:{}, routingKey:{}, msg:{}", exchange, routingKey, msg);
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
    }

    public void sendDelayMessage(String exchange, String routingKey, Object msg, int delay){
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, message -> {
            // Spring AMQP 3.x 中 setDelay(int) 已移除，改用 setDelayLong(Long)
            message.getMessageProperties().setDelayLong((long) delay);
            return message;
        });
    }

    /* Spring AMQP 3.x 中 CorrelationData#getFuture() 返回 CompletableFuture，
       原 ListenableFutureCallback#addCallback 已移除，改用 whenComplete 处理确认与重试 */
    public void sendMessageWithConfirm1(String exchange, String routingKey, Object msg, int maxRetries){
        log.debug("准备发送消息，exchange:{}, routingKey:{}, msg:{}", exchange, routingKey, msg);
        CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));

        // 通过数组持有重试计数，避免 lambda 中修改局部变量
        int[] retryHolder = new int[]{0};
        cd.getFuture().whenComplete(new BiConsumer<>() {
            @Override
            public void accept(CorrelationData.Confirm result, Throwable ex) {
                if (ex != null) {
                    log.error("处理ack回执失败", ex);
                    return;
                }
                if (result != null && !result.isAck()) {
                    log.debug("消息发送失败，收到nack，已重试次数：{}", retryHolder[0]);
                    if(retryHolder[0] >= maxRetries){
                        log.error("消息发送重试次数耗尽，发送失败");
                        return;
                    }
                    CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));
                    cd.getFuture().whenComplete(this);
                    rabbitTemplate.convertAndSend(exchange, routingKey, msg, cd);
                    retryHolder[0]++;
                }
            }
        });
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, cd);
    }

    /*用内部类保存状态*/
    public void sendMessageWithConfirm2(String exchange, String routingKey, Object msg, int maxRetries){
        log.debug("准备发送消息，exchange:{}, routingKey:{}, msg:{}", exchange, routingKey, msg);

        class RetryCallback implements BiConsumer<CorrelationData.Confirm, Throwable> {
            int retryCount = 0;

            @Override
            public void accept(CorrelationData.Confirm result, Throwable ex) {
                if (ex != null) {
                    log.error("处理ack回执失败", ex);
                    return;
                }
                if (result != null && !result.isAck()) {
                    log.debug("消息发送失败，收到nack，已重试次数：{}", retryCount);
                    if (retryCount >= maxRetries) {
                        log.error("消息发送重试次数耗尽，发送失败");
                        return;
                    }
                    retryCount++;
                    CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));
                    cd.getFuture().whenComplete(this);
                    rabbitTemplate.convertAndSend(exchange, routingKey, msg, cd);
                }
            }
        }
        RetryCallback callback = new RetryCallback();
        CorrelationData cd = new CorrelationData(UUID.randomUUID().toString(true));
        cd.getFuture().whenComplete(callback);
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, cd);
    }

}
