package com.hmall.ai.tools;

import com.hmall.api.client.ItemClient;
import com.hmall.api.client.SearchClient;
import com.hmall.api.dto.ItemDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品查询工具（供 AI 导购 Tool Calling 调用）。
 * <p>
 * 通过 Feign 调用 item-service / search-service，查询商品信息。
 */
@Component
@RequiredArgsConstructor
public class ItemTools {

    private final ItemClient itemClient;
    private final SearchClient searchClient;

    /**
     * 根据商品 id 查询商品详细信息
     */
    @Tool(description = "根据商品id查询商品的详细信息，包括名称、价格、库存、品牌、分类、规格、销量等")
    public ItemDTO queryItemById(@ToolParam(description = "商品id") Long id) {
        return itemClient.queryItemById(id);
    }

    /**
     * 批量查询商品
     */
    @Tool(description = "根据多个商品id批量查询商品信息，返回商品列表")
    public List<ItemDTO> queryItemsByIds(@ToolParam(description = "商品id列表") List<Long> ids) {
        return searchClient.queryItemByIds(ids);
    }
}
