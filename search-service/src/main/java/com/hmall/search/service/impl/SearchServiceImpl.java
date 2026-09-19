package com.hmall.search.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.ItemClient;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.po.Item;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.mapper.SearchMapper;
import com.hmall.search.service.ISearchService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.naming.directory.SearchResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl extends ServiceImpl<SearchMapper, Item> implements ISearchService {

    @Autowired(required = false)
    private RestHighLevelClient client;

    ItemClient itemClient;

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }

    @Override
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        PageDTO<ItemDoc> result = ElactisSearch(query);
        return new PageDTO<>(result.getTotal(), result.getPages(), BeanUtils.copyList(result.getList(), ItemDTO.class));
    }

    @Override
    public Map<String, List<String>> filters(ItemPageQuery query) {
        List<Item> items = this.lambdaQuery()
                .select(Item::getCategory, Item::getBrand)
                .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
                .eq(Item::getStatus, 1)
                .list();
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("category", items.stream().map(Item::getCategory)
                .filter(StrUtil::isNotBlank).distinct().sorted().collect(Collectors.toList()));
        result.put("brand", items.stream().map(Item::getBrand)
                .filter(StrUtil::isNotBlank).distinct().sorted().collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<String> suggestions(String key) {
        if (StrUtil.isBlank(key)) {
            return List.of();
        }
        return this.lambdaQuery()
                .select(Item::getName)
                .like(Item::getName, key)
                .eq(Item::getStatus, 1)
                .last("LIMIT 8")
                .list().stream().map(Item::getName).distinct().collect(Collectors.toList());
    }

    @Override
    public PageDTO<ItemDTO> queryItemByPage(PageQuery query, String truth) {
        System.out.println("wer = " + truth);
        // 1.分页查询
        Page<Item> result = this.page(query.toMpPage("update_time", false));
        // 2.封装并返回
        return PageDTO.of(result, ItemDTO.class);
    }

    @Override
    public PageDTO<ItemDoc> ElactisSearch(ItemPageQuery query) {
        PageDTO<ItemDoc> result = new PageDTO<>();
        SearchRequest searchRequest = new SearchRequest("items");
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        //精准总数
        searchRequest.source().trackTotalHits(true);

        if(query.getKey() != null && !query.getKey().isEmpty()) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("name", query.getKey())
                    .operator(org.elasticsearch.index.query.Operator.AND));
        }

        //高亮
        searchRequest.source().highlighter(
                SearchSourceBuilder.highlight()
                        .field("name")
                        .preTags("<em>")
                        .postTags("</em>")
        );
        //分页
        searchRequest.source().from(query.from()).size(query.getPageSize());
        //排序
        if(StrUtil.isNotBlank(query.getSortBy())) {
            searchRequest.source()
                    .sort(query.getSortBy(),
                    query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC);
        }else{
            searchRequest.source()
                    .sort("updateTime",
                            query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC);
        }
        // 分类过滤
        if (StrUtil.isNotBlank(query.getCategory())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }

        // 品牌过滤
        if (StrUtil.isNotBlank(query.getBrand())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }

        // 价格区间过滤
        if (query.getMinPrice() != null && query.getMaxPrice() != null) {
            boolQueryBuilder.filter(
                    QueryBuilders.rangeQuery("price")
                            .gte(query.getMinPrice())
                            .lte(query.getMaxPrice())
            );
        }
        /*这个是定义算分函数*/
        searchRequest.source().query(
                QueryBuilders.functionScoreQuery(boolQueryBuilder,
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                            new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                    QueryBuilders.termQuery("isAD", true),
                                    ScoreFunctionBuilders.weightFactorFunction(100))
                        }).boostMode(CombineFunction.MULTIPLY));

        try{
            //发起请求
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            //解析结果
            if (searchResponse.getHits() != null && searchResponse.getHits().getTotalHits() != null) {
                long totalHits = searchResponse.getHits().getTotalHits().value;
                result.setTotal(totalHits);
                result.setPages(totalHits % query.getPageSize() == 0
                        ? totalHits / query.getPageSize()
                        : totalHits / query.getPageSize() + 1);
            }else{
                result.setTotal(0L);
                result.setPages(0L);
            }

            final SearchHit[] hits = searchResponse.getHits().getHits();
            List<ItemDoc> list = new ArrayList<>();
            for(SearchHit hit : hits){
                ItemDoc itemDoc = JSONUtil.toBean(hit.getSourceAsString(), ItemDoc.class);
                Map<String, HighlightField> hfs = hit.getHighlightFields();

                if(CollUtils.isNotEmpty(hfs)){
                    HighlightField hf = hfs.get("name");
                    if(hf != null){
                        String hfName = hf.getFragments()[0].string();
                        itemDoc.setName(hfName);
                    }
                }
                list.add(itemDoc);
            }
            result.setList(list);
        } catch (IOException e) {
            log.error("Error in searching ES");
        }
        return result;
    }
}
