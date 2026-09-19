package com.hmall.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.ai.memory.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Vertex AI（Gemini）接入配置。
 * <p>
 * 通过 OpenAI 兼容端点 + 动态 access_token（authorized_user ADC）接入 Gemini。
 * 关键点：apiKey 用 NoopApiKey 占位，实际 Authorization 由 RestClient 拦截器 + WebClient filter
 * 每次请求动态注入（流式对话走 WebClient，非流式走 RestClient）。
 */
@Configuration
public class VertexAiConfig {

    @Bean
    public GoogleOAuthTokenProvider googleOAuthTokenProvider(VertexAiProperties properties,
                                                             ObjectMapper objectMapper) {
        return new GoogleOAuthTokenProvider(properties, objectMapper);
    }

    @Bean
    public OpenAiApi openAiApi(VertexAiProperties properties, GoogleOAuthTokenProvider tokenProvider) {
        // 空 headers（Vertex AI OpenAI 兼容端点不需要 x-goog-user-project）
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

        // 自定义 RestClient，拦截器动态注入 Bearer token（每次请求前刷新）
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestInterceptor(dynamicAuthInterceptor(tokenProvider));

        // 自定义 WebClient，filter 动态注入 Bearer token（流式请求用 WebClient）
        WebClient.Builder webClientBuilder = WebClient.builder()
                .filter(dynamicAuthFilter(tokenProvider));

        return OpenAiApi.builder()
                .baseUrl(properties.getOpenAiBaseUrl())   // .../locations/global/endpoints/openapi
                .apiKey(new NoopApiKey())                 // 占位，Authorization 由拦截器注入
                .headers(headers)
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
    }

    private ClientHttpRequestInterceptor dynamicAuthInterceptor(GoogleOAuthTokenProvider tokenProvider) {
        return (request, body, execution) -> {
            String token = tokenProvider.getAccessToken();
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, body);
        };
    }

    private ExchangeFilterFunction dynamicAuthFilter(GoogleOAuthTokenProvider tokenProvider) {
        return (request, next) -> {
            String token = tokenProvider.getAccessToken();
            ClientRequest authed = ClientRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();
            return next.exchange(authed);
        };
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, VertexAiProperties properties) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel())
                        .maxTokens(properties.getMaxTokens())
                        .temperature(properties.getTemperature())
                        .build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .build();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel, MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                                 RetrievalAugmentationAdvisor retrievalAugmentationAdvisor) {
        // 说明：@Tool 工具注册暂不启用（Gemini OpenAI 兼容端点 tool calling 需回传 thought_signature，
        // Spring AI OpenAI 客户端暂不支持，会导致 400）。商品推荐走 RAG Advisor 检索注入。
        return ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor, retrievalAugmentationAdvisor)
                .build();
    }

    @Bean
    public VertexAiEmbeddingModel vertexAiEmbeddingModel(VertexAiProperties properties,
                                                         GoogleOAuthTokenProvider tokenProvider,
                                                         ObjectMapper objectMapper) {
        return new VertexAiEmbeddingModel(properties, tokenProvider, objectMapper);
    }

    @Bean
    public ChatMemoryRepository chatMemoryRepository(StringRedisTemplate stringRedisTemplate) {
        return new RedisChatMemoryRepository(stringRedisTemplate);
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository,
                                 @Value("${hm.ai.memory.max:100}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
