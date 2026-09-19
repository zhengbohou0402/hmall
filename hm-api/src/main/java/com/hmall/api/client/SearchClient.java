package com.hmall.api.client;


import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hmall.api.dto.ItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@FeignClient("search-service")
public interface SearchClient {

    @GetMapping("/search/{id}")
    ItemDTO queryItemById(@PathVariable("id") Long id);

    @GetMapping("/search/items")
    //blockHandler可以不写，用Sentinel的默认降级策略
    //@SentinelResource(value = "searchService.queryItemByIds", blockHandler = "handleBlocked")
    //@SentinelResource(value = "searchService.queryItemByIds")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);
/*    default List<ItemDTO> handleBlocked(Collection<Long> ids, BlockException ex) {
        // 自定义限流降级逻辑
        return Collections.emptyList(); // 返回空列表作为降级处理
    }*/
}



