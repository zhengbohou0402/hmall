package com.hmall.search.listener;

import com.hmall.common.utils.BeanUtils;
import com.hmall.search.constants.MQConstants;
import com.hmall.search.domain.dto.ItemMQDto;
import com.hmall.search.domain.po.Item;
import com.hmall.search.service.SearchIndexService;
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
public class searchListener {
    private final SearchIndexService searchIndexService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "search.item.queue", durable = "true"),
            exchange = @Exchange(name = MQConstants.ITEM_EXCHANGE_NAME, type = "direct"),
            key = MQConstants.ITEM_QUERY_KEY))
    public void syncItem(ItemMQDto event) {
        try {
            if ("REMOVE".equals(event.getOperate())) {
                searchIndexService.remove(event.getItemDTO().getId());
            } else {
                searchIndexService.upsert(BeanUtils.copyProperties(event.getItemDTO(), Item.class));
            }
        } catch (Exception e) {
            log.error("Failed to synchronize item {} to Elasticsearch", event.getItemDTO().getId(), e);
            throw new IllegalStateException(e);
        }
    }

}
