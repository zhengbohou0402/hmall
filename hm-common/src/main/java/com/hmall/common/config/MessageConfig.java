package com.hmall.common.config;

import com.hmall.common.utils.UserContext;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;

//另一种实现
public class MessageConfig {

    // 1. 使用默认的 MessageConverter（仅序列化）
    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setCreateMessageIds(true);
        return converter;
    }

    // 2. 消费者侧逻辑仅在 RabbitTemplate 处理器中实现
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        // 生产者处理器
        template.addBeforePublishPostProcessors(message -> {
            Long userId = UserContext.getUser();
            if (userId != null) {
                message.getMessageProperties().setHeader("userInfo", userId);
            }
            return message;
        });

        // 消费者处理器（替代MessageConverter中的逻辑）
        template.addAfterReceivePostProcessors(message -> {
            Long userId = message.getMessageProperties().getHeader("userInfo");
            if (userId != null) {
                UserContext.setUser(userId); // 统一在此处设置上下文
            }
            return message;
        });

        return template;
    }
}