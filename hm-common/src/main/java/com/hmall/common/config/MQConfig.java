package com.hmall.common.config;

import cn.hutool.core.util.ObjectUtil;
import com.hmall.common.utils.UserContext;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;


@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class MQConfig {
    @Bean
    public MessageConverter messageConverter(){
        // 1.定义消息转换器
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter() {
            @Override
            public Object fromMessage(Message message) {
                Long userInfo = message.getMessageProperties().getHeader("user-info");
                log.info("user info: " + userInfo);
                if (ObjectUtil.isNotEmpty(userInfo)) {
                    UserContext.setUser(userInfo);
                }
                return super.fromMessage(message);
            }

            // 把user放在AMQP里队列
            @Override
            protected Message createMessage(Object objectToConvert, MessageProperties messageProperties,
                                            Type generticType) {
                Long userInfo = UserContext.getUser();
                if(ObjectUtil.isNotEmpty(userInfo)){
                    messageProperties.setHeader("user-info", userInfo);
                }
                return super.createMessage(objectToConvert, messageProperties, generticType);
            }
        };
        // 2.配置自动创建消息id，用于识别不同消息，也可以在业务中基于ID判断是否是重复消息
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }
}
