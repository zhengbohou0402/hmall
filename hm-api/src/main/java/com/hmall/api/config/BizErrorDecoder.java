package com.hmall.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.exception.ForbiddenException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 被调用方抛出的业务异常经过 Feign 后会退化成笼统的 "服务器内部异常"，
 * 用户看不到"优惠券已使用""订单状态不允许"这类真正有用的提示。
 * 这里把下游返回的 R{code,msg} 解析回对应的业务异常，让消息透传到最外层。
 */
@Slf4j
public class BizErrorDecoder implements ErrorDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            if (response.body() != null) {
                String body = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode node = MAPPER.readTree(body);
                if (node.hasNonNull("msg") && node.hasNonNull("code")) {
                    int code = node.get("code").asInt();
                    String msg = node.get("msg").asText();
                    switch (code) {
                        case 400:
                            return new BadRequestException(msg);
                        case 403:
                            return new ForbiddenException(msg);
                        default:
                            return new BizIllegalException(msg);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析下游错误响应失败，回退到默认解码，methodKey={}", methodKey, e);
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
