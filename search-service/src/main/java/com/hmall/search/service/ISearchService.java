package com.hmall.search.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.po.Item;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ISearchService extends IService<Item> {

    List<ItemDTO> queryItemByIds(Collection<Long> ids);
    PageDTO<ItemDTO> search(ItemPageQuery query);
    Map<String, List<String>> filters(ItemPageQuery query);
    List<String> suggestions(String key);
    PageDTO<ItemDTO> queryItemByPage(PageQuery query, @RequestHeader(value = "truth", required = false) String truth);

    PageDTO<ItemDoc> ElactisSearch(ItemPageQuery query);
}
