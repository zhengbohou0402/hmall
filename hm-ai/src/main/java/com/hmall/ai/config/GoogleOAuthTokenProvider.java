package com.hmall.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Google OAuth access token 提供器（authorized_user ADC 方式）。
 * <p>
 * 从 application_default_credentials.json 读取 refresh_token，
 * 通过 refresh_token 换取 access_token，并缓存、到期前自动刷新。
 */
@Slf4j
public class GoogleOAuthTokenProvider {

    private final VertexAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ReentrantLock lock = new ReentrantLock();

    // 凭据字段（懒加载）
    private volatile String clientId;
    private volatile String clientSecret;
    private volatile String refreshToken;

    // 缓存
    private volatile String cachedAccessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public GoogleOAuthTokenProvider(VertexAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 获取 access token，缓存未过期直接返回，否则刷新。
     */
    public String getAccessToken() {
        // 到期前 300 秒提前刷新
        if (cachedAccessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(300))) {
            return cachedAccessToken;
        }
        lock.lock();
        try {
            if (cachedAccessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(300))) {
                return cachedAccessToken;
            }
            return refreshToken();
        } finally {
            lock.unlock();
        }
    }

    private String refreshToken() {
        loadCredentials();
        String token = exchangeToken();
        cachedAccessToken = token;
        // 默认 1 小时有效期，这里保守按 50 分钟缓存
        expiresAt = Instant.now().plusSeconds(3000);
        return token;
    }

    /**
     * 从 ADC 凭据文件读取 authorized_user 字段（client_secret 必须从文件读，不猜）。
     */
    private void loadCredentials() {
        if (clientId != null) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(new java.io.File(properties.getAdcPath()));
            if (!"authorized_user".equals(root.path("type").asText())) {
                throw new IllegalStateException("ADC 凭据 type 不是 authorized_user: " + root.path("type").asText());
            }
            this.clientId = root.path("client_id").asText();
            this.clientSecret = root.path("client_secret").asText();
            this.refreshToken = root.path("refresh_token").asText();
            if (clientId.isEmpty() || clientSecret.isEmpty() || refreshToken.isEmpty()) {
                throw new IllegalStateException("ADC 凭据缺少 client_id/client_secret/refresh_token");
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取 ADC 凭据失败，请先执行 gcloud auth application-default login", e);
        }
    }

    /**
     * 调 OAuth token 端点，用 refresh_token 换 access_token（带重试，规避代理偶发 502/SSL 抖动）。
     */
    private String exchangeToken() {
        String form = "client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&grant_type=refresh_token";

        Exception last = null;
        for (int i = 0; i < 3; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getTokenUri()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String token = root.path("access_token").asText();
                    if (token.isEmpty()) {
                        throw new IllegalStateException("token 响应缺少 access_token: " + response.body());
                    }
                    return token;
                }
                last = new IllegalStateException("换 token 失败 HTTP " + response.statusCode() + ": " + response.body());
            } catch (Exception e) {
                last = e;
            }
            log.warn("换 access_token 第 {} 次失败，重试...", i + 1, last);
            sleep(2000L * (i + 1));
        }
        throw new IllegalStateException("获取 Vertex AI access_token 失败", last);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
