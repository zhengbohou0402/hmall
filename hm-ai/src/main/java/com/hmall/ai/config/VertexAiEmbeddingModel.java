package com.hmall.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Vertex AI 原生 predict 端点的 EmbeddingModel。
 * <p>
 * 注意：Vertex AI 的 OpenAI 兼容端点 /embeddings 对 embedding 模型返回 500，
 * 必须走原生端点：
 * POST /v1/projects/{p}/locations/us-central1/publishers/google/models/{model}:predict
 * 使用 gemini-embedding-001（3072 维），实测中文商品检索质量优于 text-embedding-004/005。
 */
public class VertexAiEmbeddingModel implements EmbeddingModel {

    // gemini-embedding-001（3072 维）：实测对中文商品检索质量优于 text-embedding-004/005
    private static final int DIMENSIONS = 3072;
    private static final String MODEL = "gemini-embedding-001";

    private final VertexAiProperties properties;
    private final GoogleOAuthTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public VertexAiEmbeddingModel(VertexAiProperties properties,
                                  GoogleOAuthTokenProvider tokenProvider,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            float[] vector = embedSingle(texts.get(i));
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedSingle(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * 单个文本向量化（带重试，规避代理偶发 SSL 抖动）。
     */
    private float[] embedSingle(String text) {
        String url = "https://aiplatform.googleapis.com/v1/projects/" + properties.getProjectId()
                + "/locations/us-central1/publishers/google/models/" + MODEL + ":predict";
        String body = "{\"instances\":[{\"content\":\"" + escapeJson(text) + "\"}]}";

        Exception last = null;
        for (int i = 0; i < 3; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode values = root.path("predictions").path(0).path("embeddings").path("values");
                    float[] vector = new float[values.size()];
                    for (int j = 0; j < values.size(); j++) {
                        vector[j] = (float) values.get(j).asDouble();
                    }
                    return vector;
                }
                last = new IllegalStateException("embedding 失败 HTTP " + response.statusCode() + ": " + response.body());
            } catch (Exception e) {
                last = e;
            }
            sleep(2000L * (i + 1));
        }
        throw new IllegalStateException("获取 embedding 失败", last);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
