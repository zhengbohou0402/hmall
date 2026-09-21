package com.hmall.item.controller;

import com.hmall.api.dto.ItemDTO;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.domain.PageDTO;


import com.hmall.item.constants.MQConstants;
import com.hmall.item.domain.dto.ItemMQDto;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.domain.enums.ItemOperate;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.query.ItemPageQuery;
import com.hmall.item.service.IItemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "商品管理相关接口")
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final RabbitTemplate rabbitTemplate;
    private final IItemService itemService;

    @Operation(summary = "后台分页查询商品")
    @GetMapping("/page")
    public PageDTO<ItemDTO> queryItemPage(ItemPageQuery query,
                                           @RequestParam(value = "id", required = false) Long id) {
        var page = itemService.lambdaQuery()
                .eq(id != null, Item::getId, id)
                .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
                .eq(StrUtil.isNotBlank(query.getCategory()), Item::getCategory, query.getCategory())
                .eq(StrUtil.isNotBlank(query.getBrand()), Item::getBrand, query.getBrand())
                .ge(query.getMinPrice() != null, Item::getPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, Item::getPrice, query.getMaxPrice())
                .page(query.toMpPage("update_time", false));
        return PageDTO.of(page, ItemDTO.class);
    }

    @Operation(summary = "后台经营概览真实统计")
    @GetMapping("/admin/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", itemService.count());
        result.put("onSale", itemService.lambdaQuery().eq(Item::getStatus, 1).count());
        result.put("offSale", itemService.lambdaQuery().eq(Item::getStatus, 2).count());
        result.put("lowStock", itemService.lambdaQuery()
                .eq(Item::getStatus, 1)
                .le(Item::getStock, 10)
                .count());

        QueryWrapper<Item> categoryQuery = new QueryWrapper<Item>()
                .select("COALESCE(category, '未分类') AS category", "COUNT(*) AS count")
                .groupBy("category")
                .orderByDesc("count")
                .last("LIMIT 12");
        result.put("categories", itemService.listMaps(categoryQuery));
        result.put("lowStockItems", itemService.lambdaQuery()
                .eq(Item::getStatus, 1)
                .le(Item::getStock, 10)
                .orderByAsc(Item::getStock)
                .last("LIMIT 6")
                .list());
        return result;
    }

    @Operation(summary = "根据id查询商品")
    @GetMapping("{id}")
    public ItemDTO queryItemById(@PathVariable("id") Long id) {
        return itemService.queryItemById(id);
    }

    @Operation(summary = "新增商品")
    @PostMapping
    public void saveItem(@RequestBody ItemDTO item) {
        // 新增
        //itemService.save(BeanUtils.copyBean(item, Item.class));
        itemService.addItem(item);
    }

    @Operation(summary = "更新商品状态")
    @PutMapping("/status/{id}/{status}")
    public void updateItemStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status){
        Item item = new Item();
        item.setId(id);
        item.setStatus(status);
        itemService.updateById(item);
    }

    @Operation(summary = "更新商品")
    @PutMapping
    public void updateItem(@RequestBody ItemDTO item) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        item.setStatus(null);
        // 更新
        itemService.updateById(BeanUtils.copyBean(item, Item.class));
        rabbitTemplate.convertAndSend(
                MQConstants.ITEM_EXCHANGE_NAME,
                MQConstants.ITEM_QUERY_KEY,
                new ItemMQDto(
                        ItemOperate.UPDATE,
                        item));
    }

    @Operation(summary = "根据id删除商品")
    @DeleteMapping("{id}")
    public void deleteItemById(@PathVariable("id") Long id) {
        itemService.removeById(id);
        rabbitTemplate.convertAndSend(
                MQConstants.ITEM_EXCHANGE_NAME,
                MQConstants.ITEM_QUERY_KEY,
                new ItemMQDto(
                        ItemOperate.REMOVE,
                        ItemDTO.builder().id(id).build()));
    }

    @Operation(summary = "批量扣减库存")
    @PutMapping("/stock/deduct")
    public void deductStock(@RequestBody List<OrderDetailDTO> items){
        itemService.deductStock(items);
    }

    @Operation(summary = "批量恢复库存")
    @PutMapping("/stock/restore")
    public void restoreStock(@RequestBody List<OrderDetailDTO> itemsDto){
        itemService.restoreStock(itemsDto);
    }
}
