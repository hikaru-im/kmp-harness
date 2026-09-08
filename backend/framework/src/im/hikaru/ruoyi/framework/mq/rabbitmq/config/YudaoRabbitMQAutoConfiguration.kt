package im.hikaru.ruoyi.framework.mq.rabbitmq.config

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * RabbitMQ 消息队列配置类 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
@AutoConfiguration
@ConditionalOnClass(name = ["org.springframework.amqp.rabbit.core.RabbitTemplate"])
class YudaoRabbitMQAutoConfiguration {

    /**
     * JacksonJsonMessageConverter Bean：使用 jackson 序列化消息
     */
    @Bean
    fun createMessageConverter(): MessageConverter = JacksonJsonMessageConverter()
}
