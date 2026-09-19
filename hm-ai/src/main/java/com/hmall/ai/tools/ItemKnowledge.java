package com.hmall.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 商品知识条目（用于 RAG 向量检索）。
 */
@Data
@AllArgsConstructor
public class ItemKnowledge {

    /** 商品 id */
    private Long id;
    /** 商品名称 */
    private String name;
    /** 品牌 */
    private String brand;
    /** 分类 */
    private String category;
    /** 规格 */
    private String spec;
    /** 价格（分） */
    private Integer price;
    /** 描述（用于向量化与检索的文本） */
    private String description;

    /**
     * 拼接成用于 embedding 的文本
     */
    public String toEmbeddingText() {
        return name + "，品牌" + brand + "，分类" + category + "，规格" + spec
                + "，价格" + (price / 100.0) + "元。" + description;
    }
}
