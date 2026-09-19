package com.hmall.item.Listener;

import com.hmall.item.constants.MQConstants;
import com.hmall.item.domain.dto.ReviewCreatedEvent;
import com.hmall.item.domain.po.Item;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewListener {

    private final IItemService itemService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstants.REVIEW_QUEUE_NAME),
            exchange = @Exchange(name = MQConstants.REVIEW_EXCHANGE_NAME, delayed = "false"),
            key = MQConstants.REVIEW_CREATE_KEY
    ))
    public void listenReviewCreated(ReviewCreatedEvent event) {
        if (event.getItemId() == null) {
            return;
        }
        log.info("收到评论创建事件，itemId={}，累加comment_count", event.getItemId());
        itemService.lambdaUpdate()
                .setSql("comment_count = comment_count + 1")
                .eq(Item::getId, event.getItemId())
                .update();
    }
}
