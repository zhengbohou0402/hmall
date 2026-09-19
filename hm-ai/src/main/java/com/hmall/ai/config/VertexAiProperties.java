package com.hmall.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Vertex AI 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "hm.ai.vertex")
public class VertexAiProperties {

    /** GCP 项目 ID */
    private String projectId = "rag-fist";

    /** Vertex AI 区域，gemini-3.6-flash 仅在 global 可用 */
    private String location = "global";

    /** 模型名（需带 google/ 前缀） */
    private String model = "google/gemini-3.6-flash";

    /** ADC 凭据文件路径（authorized_user） */
    private String adcPath = System.getProperty("user.home") + "/.config/gcloud/application_default_credentials.json";

    /** 最大输出 token 数 */
    private Integer maxTokens = 2048;

    /** 温度 */
    private Double temperature = 0.7;

    /** OAuth token 端点 */
    private String tokenUri = "https://oauth2.googleapis.com/token";

    /**
     * Vertex AI OpenAI 兼容端点（global）
     */
    public String getOpenAiBaseUrl() {
        return "https://aiplatform.googleapis.com/v1/projects/" + projectId
                + "/locations/" + location + "/endpoints/openapi";
    }
}
