package com.hmall.common.config;


import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;

/**
 * RabbitMQ 消费端错误消息兜底处理配置（Republish）
 */
@Configuration
@ConditionalOnProperty(value = "spring.rabbitmq.listener.simple.retry.enabled", havingValue = "true")
public class MQConsumeErrorAutoConfiguration {

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * 错误交换机（可复用）
     */
    @Bean
    public DirectExchange errorDirectExchange() {
        return ExchangeBuilder
                .directExchange("error.direct")
                .durable(true)
                .build();
    }

    /**
     * 当前服务专属错误队列
     */
    @Bean
    public Queue serviceErrorQueue() {
        return QueueBuilder
                .durable(serviceName + ".error.queue")
                .build();
    }

    /**
     * 绑定错误队列到错误交换机，routingKey 就是服务名
     */
    @Bean
    public Binding serviceBinding() {
        return BindingBuilder
                .bind(serviceErrorQueue())
                .to(errorDirectExchange())
                .with(serviceName);
    }

    /**
     * 消费失败兜底策略：重试次数耗尽后，转发到 error.exchange 中
     */
    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, "error.direct", serviceName);
    }

    /**
     * 可选：设置错误处理器，打印更清晰的异常日志（可省略）
     */
    @Bean
    public ErrorHandler errorHandler() {
        return new ConditionalRejectingErrorHandler(new ConditionalRejectingErrorHandler.DefaultExceptionStrategy());
    }

    // 如果你手动定义了 RabbitListenerContainerFactory，可以注入上面 errorHandler
    // 不定义也没问题，Spring 会自动接管
}

