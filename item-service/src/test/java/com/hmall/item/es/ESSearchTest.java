package com.hmall.item.es;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.hmall.item.domain.po.ItemDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ESSearchTest {
    private RestHighLevelClient client;
    @BeforeEach
    void setUp() {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("localhost:9200")
        ));
    }

    @Test
    void testMatchAll() throws IOException {
        SearchRequest request = new SearchRequest("items");
        request.source()
                .query(QueryBuilders.matchAllQuery());

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        SearchHits searchHits = response.getHits();

        long total = searchHits.getTotalHits().value;
        System.out.println("total = " + total);
        SearchHit[] hits = searchHits.getHits();

        for (SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            ItemDoc doc = JSON.parseObject(json, ItemDoc.class);
            System.out.println("doc = " + doc);
        }
    }

    @Test
    void testBool() throws IOException {
        SearchRequest request = new SearchRequest("items");
        BoolQueryBuilder bool = QueryBuilders.boolQuery();

        bool.must(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        bool.filter(QueryBuilders.matchQuery("name", "蒙牛"));
        bool.filter(QueryBuilders.rangeQuery("price").lte(30000));

        request.source().query(bool);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponseResult(response);
    }

    @Test
    void testPageAndSort() throws IOException {
        int pageNo = 1;
        int pageSize = 20;

        SearchRequest request = new SearchRequest("items");
        request.source().query(QueryBuilders.matchAllQuery());
        request.source().sort("sold", SortOrder.ASC);
        request.source().sort("price", SortOrder.ASC);
        request.source().from((pageNo - 1) * pageSize).size(pageSize);
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponseResult(response);
    }

    @Test
    void testHighlight() throws IOException {
        SearchRequest request = new SearchRequest("items");

        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"));

        request.source().highlighter(
                SearchSourceBuilder.highlight()
                        .field("name")
                        .preTags("<em>")
                        .postTags("</em>")
        );
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponseResult(response);
    }

    private static void parseResponseResult(SearchResponse response) {
        /*SearchHits searchHits = response.getHits();
        // 4.1.总条数
        long total = searchHits.getTotalHits().value;
        System.out.println("total = " + total);
        // 4.2.命中的数据
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            // 4.2.1.获取source结果
            String json = hit.getSourceAsString();
            // 4.2.2.转为ItemDoc
            ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
            // 4.3.处理高亮结果
            Map<String, HighlightField> hfs = hit.getHighlightFields();
            if(hfs != null && !hfs.isEmpty()){
                // 4.3.1.根据高亮字段名获取高亮结果
                HighlightField hf = hfs.get("name");
                // 4.3.2.获取高亮结果，覆盖非高亮结果
                String hfName = hf.getFragments()[0].string();
                doc.setName(hfName);
            }
            System.out.println("doc = " + doc);
        }*/

        SearchHits searchHits = response.getHits();
        long total = searchHits.getTotalHits().value;
        System.out.println("total = " + total);
        SearchHit[] hits = searchHits.getHits();
        for(SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            ItemDoc doc = JSON.parseObject(json, ItemDoc.class);
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();

            if(highlightFields != null && !highlightFields.isEmpty()) {
                HighlightField highlightField = highlightFields.get("name");
                String hfName = highlightField.getFragments()[0].string();
                doc.setName(hfName);
            }
            System.out.println("doc = " + doc); // ✅ 这一句被你删掉了

        }
    }

    @Test
    void testAgg() throws IOException {
       SearchRequest request = new SearchRequest("items");
       BoolQueryBuilder bool = QueryBuilders.boolQuery()
               .filter(QueryBuilders.termQuery("category", "手机"))
               .filter(QueryBuilders.rangeQuery("price").gte(300000));
       request.source().query(bool).size(0);

       request.source().aggregation(
            AggregationBuilders.terms("brand_agg").field("brand").size(5)
       );

       SearchResponse response = client.search(request, RequestOptions.DEFAULT);
       Aggregations aggregations = response.getAggregations();
       Terms terms = aggregations.get("brand_agg");
       List<? extends Terms.Bucket> buckets = terms.getBuckets();

       for(Terms.Bucket bucket : buckets) {
           String brand = bucket.getKeyAsString();
           System.out.println("brand = " + brand);
           long count = bucket.getDocCount();
           System.out.println("count = " + count);
       }
    }

    @AfterEach
    void tearDown() throws IOException {
        if(client != null){
            client.close();
        }
    }
}
