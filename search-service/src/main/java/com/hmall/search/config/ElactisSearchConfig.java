package com.hmall.search.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElactisSearchConfig {

    @Value("${elasticsearch.host:localhost}") // 从配置读取，默认localhost
    private String host;

    @Value("${elasticsearch.port:9200}")     // 从配置读取，默认9200
    private int port;                        // 必须用int类型

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient() {
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, "http"))
        );
    }


}
