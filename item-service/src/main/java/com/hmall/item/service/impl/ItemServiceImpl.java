package com.hmall.item.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.SearchClient;

import com.hmall.api.dto.ItemDTO;
import com.hmall.common.exception.BizIllegalException;

import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.MultiLevelCacheUtils;
import com.hmall.item.constants.MQConstants;
import com.hmall.item.domain.dto.ItemMQDto;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.domain.enums.ItemOperate;
import com.hmall.item.domain.po.Item;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private static final String ITEM_DETAIL_CACHE_KEY_PREFIX = "item:detail:";
    private static final Duration ITEM_DETAIL_REDIS_TTL = Duration.ofMinutes(30);

    private final SearchClient searchClient;
    private final RabbitTemplate rabbitTemplate;
    private final MultiLevelCacheUtils multiLevelCacheUtils;

    @Override
    public void deductStock(List<OrderDetailDTO> items) {
        // 原实现委托给一个不存在的 MyBatis XML 语句 id，运行时必抛 "Invalid bound statement"；
        // 改为 LambdaUpdateWrapper 的原子扣减，SQL 层用 stock >= num 做并发安全的库存守卫
        for (OrderDetailDTO item : items) {
            boolean updated = lambdaUpdate()
                    .setSql("stock = stock - " + item.getNum())
                    .eq(Item::getId, item.getItemId())
                    .ge(Item::getStock, item.getNum())
                    .update();
            if (!updated) {
                throw new BizIllegalException("库存不足！");
            }
            evictItemCache(item.getItemId());
        }
    }

    @Override
    public void restoreStock(List<OrderDetailDTO> itemsDto) {
        // 原实现把库存数值写进了 Item::getName，且存在 lambdaQuery 与 lambdaUpdate 之间的读写竞态；
        // 改为 setSql 原子递增，与 deductStock 保持一致的并发安全写法
        for (OrderDetailDTO itemDto : itemsDto) {
            lambdaUpdate()
                    .setSql("stock = stock + " + itemDto.getNum())
                    .eq(Item::getId, itemDto.getItemId())
                    .update();
            evictItemCache(itemDto.getItemId());
        }
    }

    @Override
    public ItemDTO queryItemById(Long id) {
        // 多级缓存：Caffeine(L1) -> Redis(L2) -> 回源到 search-service 的 ES 查询；
        // 写操作(update/delete/deductStock/restoreStock)完成后主动 evict，TTL 只是兜底
        return multiLevelCacheUtils.get(
                ITEM_DETAIL_CACHE_KEY_PREFIX + id,
                ItemDTO.class,
                key -> searchClient.queryItemById(id),
                ITEM_DETAIL_REDIS_TTL
        );
    }

    @Override
    public boolean updateById(Item entity) {
        boolean result = super.updateById(entity);
        evictItemCache(entity.getId());
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        evictItemCache((Long) id);
        return result;
    }

    private void evictItemCache(Long id) {
        multiLevelCacheUtils.evict(ITEM_DETAIL_CACHE_KEY_PREFIX + id);
    }

    //简单直接的回答是：因为数据库的主键ID是自增的，在插入数据后才生成。我们需要把这个新生成的、
    // 唯一的ID从数据库实体（Item）同步回数据传输对象（DTO），以便后续流程（如发消息到MQ）能使用这个正确的ID。
    @Override
    public void addItem(ItemDTO itemDTO) {
        Item item = BeanUtils.copyProperties(itemDTO, Item.class);
        baseMapper.insert(item);
        /*您的 Item 实体类使用了 @TableId(type = IdType.AUTO)，这意味着主键ID是由数据库在插入数据时自动生成的。
        在执行 baseMapper.insert(item) 之前，item 对象的 id 字段为 null。
        执行 insert 操作后，数据库会生成一个新的、唯一的自增ID，并通过MyBatis-Plus回填到 item 对象的 id 字段中。*/
        itemDTO.setId(item.getId());

        rabbitTemplate.convertAndSend(
                MQConstants.ITEM_EXCHANGE_NAME,
                MQConstants.ITEM_QUERY_KEY,
                new ItemMQDto(ItemOperate.ADD, itemDTO)
        );
    }
}
