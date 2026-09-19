package com.hmall.item.Listener;
import cn.hutool.json.JSONUtil;
import com.hmall.api.dto.ItemDTO;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.constants.ElasticConstants;
import com.hmall.item.constants.MQConstants;
import com.hmall.item.domain.dto.ItemMQDto;
import com.hmall.item.domain.po.ItemDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class ItemListener {

    private final RestHighLevelClient client;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstants.DELAY_ORDER_QUEUE_NAME),
            exchange = @Exchange(name = MQConstants.ITEM_EXCHANGE_NAME, delayed = "false"),
            key = MQConstants.ITEM_QUERY_KEY
    ))
    public void listenItemMessage(ItemMQDto itemMQDto) {
        if(itemMQDto.getItemDTO().getId() == null){
            return;
        }
        switch (itemMQDto.getOperate()){
            case ADD:
                addItemByIndex(itemMQDto.getItemDTO());
                break;
            case REMOVE:
                removeItemByIndex(itemMQDto.getItemDTO());
                break;
            case UPDATE:
                updateItemByIndex(itemMQDto.getItemDTO());
                break;
        }
    }

    private void removeItemByIndex(ItemDTO item) {
        //直接根据文档Id删除索引库中的商品
        log.info("移除索引库中的商品" + item.getId());
        DeleteRequest request = new DeleteRequest(ElasticConstants.ITEM_INDEX_NAME)
                .id(item.getId().toString());
        try {
            client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.info("移除索引库中的商品出错了:{}", e.getMessage());
        }
    }

    private void updateItemByIndex(ItemDTO item) {
        UpdateRequest request = new UpdateRequest(ElasticConstants.ITEM_INDEX_NAME,
                item.getId().toString());
        ItemDoc itemDoc = BeanUtils.copyProperties(item, ItemDoc.class);
        itemDoc.setUpdateTime(LocalDateTime.now());
        request.doc(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        try{
            client.update(request, RequestOptions.DEFAULT);
        }catch (IOException e) {
            log.info("修改索引库商品出错了：{}", e.getMessage());
        }
    }

    private void addItemByIndex(ItemDTO itemDTO) {
        log.info("Adding item to Elasticsearch from database.");
        ItemDoc itemDoc = BeanUtils.copyProperties(itemDTO, ItemDoc.class);
        IndexRequest request = new IndexRequest(ElasticConstants.ITEM_INDEX_NAME)
                .id(itemDTO.getId().toString());
        request.source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        try{
            client.index(request, RequestOptions.DEFAULT);
        }catch(IOException e) {
            log.info("Failed to add the item to the index: {}", e.getMessage());
        }
    }
}
