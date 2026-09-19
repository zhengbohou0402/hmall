package com.hmall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.hmall.common.domain.R;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * 给网关补上边缘限流——之前只有 cart-service 用 Sentinel 做出站 Feign 熔断，网关本身完全没有流控。
 * SentinelGatewayFilter/SentinelGatewayBlockExceptionHandler 由 spring-cloud-alibaba-sentinel-gateway
 * 的自动配置(SentinelSCGAutoConfiguration)提供，这里只需要注册规则和自定义被限流时的响应体，不用再手动定义那两个 Bean。
 */
@Configuration
public class GatewayFlowConfig {

    @PostConstruct
    public void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        // 全局默认：每个已注册路由每秒最多 50 个请求
        rules.add(new GatewayFlowRule("item-service").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("cart-service").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("user-service-users").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("user-service-addresses").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("trade-service").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("pay-service").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("search-service").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("review-service").setCount(50).setIntervalSec(1));
        // 秒杀接口是防刷的重点：单独收紧到每秒 5 个，路由 id 需要和后面 seckill-service 在 Nacos 注册的路由一致
        rules.add(new GatewayFlowRule("seckill-service").setCount(5).setIntervalSec(1));
        GatewayRuleManager.loadRules(rules);

        GatewayCallbackManager.setBlockHandler(defaultBlockRequestHandler());
    }

    /**
     * 被限流时返回和其它业务异常一致的 R 格式，而不是 Sentinel 默认的纯文本
     */
    private BlockRequestHandler defaultBlockRequestHandler() {
        return (exchange, t) -> ServerResponse
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(R.error(429, "请求过于频繁，请稍后再试"));
    }
}
