package com.hmall.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "hm.auth")
public class AuthProperties {
    private List<String> includePaths;
    private List<String> excludePaths;
    /**
     * 仅在 GET 请求下生效的公开路径，用于区分同一路径形状下的公开读（GET）与需要鉴权的写（PUT/DELETE 等），
     * 例如 /items/{id} 的 GET 应公开，但 DELETE /items/{id} 必须鉴权，AntPathMatcher 无法仅凭路径区分两者
     */
    private List<String> excludeGetPaths;
}
