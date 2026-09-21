package com.hmall.search.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.BeanUtils;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.service.ISearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {
    private final ISearchService searchService;

    @Operation(summary = "分页查询商品")
    @GetMapping("/page")
    public PageDTO<ItemDTO> queryItemByPage(PageQuery query, @RequestHeader(value = "truth", required = false) String truth) {
        return searchService.queryItemByPage(query, truth);
    }

    @Operation(summary = "根据id批量查询商品")
    @GetMapping("/items")
    public List<ItemDTO> queryItemByIds(@RequestParam("ids") List<Long> ids){
//        ThreadUtil.sleep(500);
        return searchService.queryItemByIds(ids);
    }

    @Operation(summary = "根据id查询商品")
    @GetMapping("{id}")
    public ItemDTO queryItemById(@PathVariable("id") Long id) {
        return BeanUtils.copyBean(searchService.getById(id), ItemDTO.class);
    }

    @Operation(summary = "搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        return searchService.search(query);
    }

    @PostMapping("/filters")
    public Map<String, List<String>> filters(@RequestBody ItemPageQuery query) {
        return searchService.filters(query);
    }

    @GetMapping("/suggestion")
    public List<String> suggestions(@RequestParam String key) {
        return searchService.suggestions(key);
    }
}
