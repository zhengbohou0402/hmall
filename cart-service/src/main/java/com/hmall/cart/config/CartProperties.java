package com.hmall.cart.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hm.cart")
public class CartProperties {
    /** Local default when the optional Nacos cart configuration is unavailable. */
    private Integer maxItems = 100;
}
