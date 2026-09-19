package com.hmall.search.service;

import com.hmall.search.constants.ElasticConstants;
import com.hmall.search.domain.po.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** Maintains the ES product read model from the product database. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {
    private final RestHighLevelClient client;
    private final ISearchService searchService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            if (!client.indices().exists(new GetIndexRequest(ElasticConstants.ITEM_INDEX_NAME), RequestOptions.DEFAULT)) {
                rebuild();
            }
        } catch (IOException e) {
            log.error("Unable to initialize Elasticsearch product index", e);
        }
    }

    public synchronized void rebuild() throws IOException {
        if (client.indices().exists(new GetIndexRequest(ElasticConstants.ITEM_INDEX_NAME), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(ElasticConstants.ITEM_INDEX_NAME), RequestOptions.DEFAULT);
        }
        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request("PUT", "/" + ElasticConstants.ITEM_INDEX_NAME);
        request.setJsonEntity("""
                {"mappings":{"properties":{
                  "name":{"type":"text","analyzer":"standard"},
                  "brand":{"type":"keyword"},"category":{"type":"keyword"},
                  "price":{"type":"integer"},"sold":{"type":"integer"},
                  "commentCount":{"type":"integer"},"isAD":{"type":"boolean"},
                  "status":{"type":"integer"},"updateTime":{"type":"date"}
                }}}
                """);
        client.getLowLevelClient().performRequest(request);

        long total = 0;
        long pageNo = 1;
        while (true) {
            Page<Item> page = searchService.page(new Page<>(pageNo++, 200));
            if (page.getRecords().isEmpty()) {
                break;
            }
            BulkRequest bulk = new BulkRequest();
            for (Item item : page.getRecords()) {
                bulk.add(indexRequest(item));
            }
            client.bulk(bulk, RequestOptions.DEFAULT);
            total += page.getRecords().size();
            if (pageNo > page.getPages()) {
                break;
            }
        }
        log.info("Rebuilt ES product index with {} documents", total);
    }

    public void upsert(Item item) throws IOException {
        client.index(indexRequest(item), RequestOptions.DEFAULT);
    }

    public void remove(Long id) throws IOException {
        client.delete(new org.elasticsearch.action.delete.DeleteRequest(ElasticConstants.ITEM_INDEX_NAME, String.valueOf(id)), RequestOptions.DEFAULT);
    }

    private IndexRequest indexRequest(Item item) {
        return new IndexRequest(ElasticConstants.ITEM_INDEX_NAME)
                .id(String.valueOf(item.getId()))
                .opType(DocWriteRequest.OpType.INDEX)
                .source(cn.hutool.json.JSONUtil.toJsonStr(item), XContentType.JSON);
    }

}
