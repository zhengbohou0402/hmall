package com.hmall.ai.config;

import com.hmall.ai.rag.ItemRepository;
import com.hmall.api.dto.ItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * RAG 商品推荐配置：
 * 1. 从 hm-item 库抽样加载商品，embedding 向量化后存入内存向量库（SimpleVectorStore）
 * 2. RetrievalAugmentationAdvisor 在对话前检索最相关商品，注入提示词上下文
 * <p>
 * 采用 Advisor 检索注入（而非 Tool Calling），绕开 Gemini OpenAI 兼容端点的
 * thought_signature 限制。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ItemRagConfig {

    private final ItemRepository itemRepository;
    private final VertexAiEmbeddingModel embeddingModel;

    @Bean
    public VectorStore itemVectorStore() {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        try {
            // 抽样加载商品并向量化
            List<ItemDTO> items = itemRepository.sampleItems();
            List<Document> documents = items.stream()
                    .map(this::toDocument)
                    .toList();
            store.add(documents);
            log.info("商品知识库向量化完成，共 {} 件商品", documents.size());
        } catch (Exception e) {
            log.warn("商品知识库初始化失败（MySQL 不可用？），RAG 降级为空知识库: {}", e.getMessage());
        }
        return store;
    }

    @Bean
    public DocumentRetriever itemDocumentRetriever(VectorStore itemVectorStore) {
        return query -> {
            // similarityThresholdAll：不按相似度阈值过滤，取 topK 即可（SimpleVectorStore 默认阈值可能过滤全部）
            List<Document> docs = itemVectorStore.similaritySearch(
                    SearchRequest.builder().query(query.text()).topK(3).similarityThresholdAll().build());
            log.debug("RAG 检索 query={}，命中 {} 条: {}", query.text(), docs.size(),
                    docs.stream().map(d -> d.getMetadata().get("name")).toList());
            return docs;
        };
    }

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(DocumentRetriever itemDocumentRetriever) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(itemDocumentRetriever)
                .build();
    }

    /**
     * 商品 → 向量文档
     */
    private Document toDocument(ItemDTO item) {
        String content = String.format("%s，品牌%s，分类%s，规格%s，价格%.2f元",
                item.getName(), item.getBrand(), item.getCategory(),
                item.getSpec() == null ? "" : item.getSpec(),
                item.getPrice() / 100.0);
        Map<String, Object> metadata = Map.of(
                "id", item.getId(),
                "name", item.getName(),
                "price", item.getPrice(),
                "category", item.getCategory() == null ? "" : item.getCategory(),
                "brand", item.getBrand() == null ? "" : item.getBrand(),
                "spec", item.getSpec() == null ? "" : item.getSpec()
        );
        return new Document(content, metadata);
    }
}
