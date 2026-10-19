package im.hikaru.ruoyi.framework.websocket.config

import im.hikaru.ruoyi.framework.mq.redis.config.YudaoRedisMQConsumerAutoConfiguration
import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.websocket.core.handler.JsonWebSocketMessageHandler
import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketMessageListener
import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketSessionLifecycleListener
import im.hikaru.ruoyi.framework.websocket.core.security.LoginUserHandshakeInterceptor
import im.hikaru.ruoyi.framework.websocket.core.security.WebSocketAuthorizeRequestsCustomizer
import im.hikaru.ruoyi.framework.websocket.core.sender.kafka.KafkaWebSocketMessageConsumer
import im.hikaru.ruoyi.framework.websocket.core.sender.kafka.KafkaWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.sender.local.LocalWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq.RabbitMQWebSocketMessageConsumer
import im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq.RabbitMQWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.sender.redis.RedisWebSocketMessageConsumer
import im.hikaru.ruoyi.framework.websocket.core.sender.redis.RedisWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq.RocketMQWebSocketMessageConsumer
import im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq.RocketMQWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionHandlerDecorator
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManagerImpl
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.server.HandshakeInterceptor

@AutoConfiguration(before = [YudaoRedisMQConsumerAutoConfiguration::class])
@EnableWebSocket
@ConditionalOnProperty(prefix = "yudao.websocket", name = ["enable"], matchIfMissing = true)
@EnableConfigurationProperties(WebSocketProperties::class)
class YudaoWebSocketAutoConfiguration {

    @Bean
    fun webSocketConfigurer(
        handshakeInterceptors: Array<HandshakeInterceptor>,
        webSocketHandler: WebSocketHandler,
        webSocketProperties: WebSocketProperties,
    ): WebSocketConfigurer = WebSocketConfigurer { registry ->
        registry.addHandler(webSocketHandler, webSocketProperties.path)
            .addInterceptors(*handshakeInterceptors)
            .setAllowedOriginPatterns("*")
    }

    @Bean
    fun handshakeInterceptor(): HandshakeInterceptor = LoginUserHandshakeInterceptor()

    @Bean
    fun webSocketHandler(
        sessionManager: WebSocketSessionManager,
        messageListeners: List<WebSocketMessageListener<*>>,
        lifecycleListeners: List<WebSocketSessionLifecycleListener>,
    ): WebSocketHandler {
        val messageHandler = JsonWebSocketMessageHandler(messageListeners)
        return WebSocketSessionHandlerDecorator(messageHandler, sessionManager, lifecycleListeners)
    }

    @Bean
    fun webSocketSessionManager(): WebSocketSessionManager = WebSocketSessionManagerImpl()

    @Bean
    fun webSocketAuthorizeRequestsCustomizer(
        webSocketProperties: WebSocketProperties,
    ): WebSocketAuthorizeRequestsCustomizer = WebSocketAuthorizeRequestsCustomizer(webSocketProperties)

    @Configuration
    @ConditionalOnBean(YudaoWebSocketAutoConfiguration::class)
    @ConditionalOnProperty(prefix = "yudao.websocket", name = ["sender-type"], havingValue = "local")
    class LocalWebSocketMessageSenderConfiguration {
        @Bean
        fun localWebSocketMessageSender(sessionManager: WebSocketSessionManager): LocalWebSocketMessageSender =
            LocalWebSocketMessageSender(sessionManager)
    }

    @Configuration
    @ConditionalOnBean(YudaoWebSocketAutoConfiguration::class)
    @ConditionalOnProperty(prefix = "yudao.websocket", name = ["sender-type"], havingValue = "redis")
    class RedisWebSocketMessageSenderConfiguration {
        @Bean
        fun redisWebSocketMessageSender(
            sessionManager: WebSocketSessionManager,
            redisMQTemplate: RedisMQTemplate,
        ): RedisWebSocketMessageSender = RedisWebSocketMessageSender(sessionManager, redisMQTemplate)

        @Bean
        fun redisWebSocketMessageConsumer(
            redisWebSocketMessageSender: RedisWebSocketMessageSender,
        ): RedisWebSocketMessageConsumer = RedisWebSocketMessageConsumer(redisWebSocketMessageSender)
    }

    @Configuration
    @ConditionalOnBean(YudaoWebSocketAutoConfiguration::class)
    @ConditionalOnClass(name = ["org.apache.rocketmq.spring.core.RocketMQTemplate"])
    @ConditionalOnProperty(prefix = "yudao.websocket", name = ["sender-type"], havingValue = "rocketmq")
    class RocketMQWebSocketMessageSenderConfiguration {
        @Bean
        fun rocketMQWebSocketMessageSender(
            sessionManager: WebSocketSessionManager,
            rocketMQTemplate: RocketMQTemplate,
            @Value("\${yudao.websocket.sender-rocketmq.topic}") topic: String,
        ): RocketMQWebSocketMessageSender =
            RocketMQWebSocketMessageSender(sessionManager, rocketMQTemplate, topic)

        @Bean
        fun rocketMQWebSocketMessageConsumer(
            sender: RocketMQWebSocketMessageSender,
        ): RocketMQWebSocketMessageConsumer = RocketMQWebSocketMessageConsumer(sender)
    }

    @Configuration
    @ConditionalOnBean(YudaoWebSocketAutoConfiguration::class)
    @ConditionalOnClass(name = ["org.springframework.amqp.rabbit.core.RabbitTemplate"])
    @ConditionalOnProperty(prefix = "yudao.websocket", name = ["sender-type"], havingValue = "rabbitmq")
    class RabbitMQWebSocketMessageSenderConfiguration {
        @Bean
        fun rabbitMQWebSocketMessageSender(
            sessionManager: WebSocketSessionManager,
            rabbitTemplate: RabbitTemplate,
            websocketTopicExchange: TopicExchange,
        ): RabbitMQWebSocketMessageSender =
            RabbitMQWebSocketMessageSender(sessionManager, rabbitTemplate, websocketTopicExchange)

        @Bean
        fun rabbitMQWebSocketMessageConsumer(
            sender: RabbitMQWebSocketMessageSender,
        ): RabbitMQWebSocketMessageConsumer = RabbitMQWebSocketMessageConsumer(sender)

        @Bean
        fun websocketTopicExchange(
            @Value("\${yudao.websocket.sender-rabbitmq.exchange}") exchange: String,
        ): TopicExchange = TopicExchange(exchange, true, false)
    }

    @Configuration
    @ConditionalOnBean(YudaoWebSocketAutoConfiguration::class)
    @ConditionalOnClass(name = ["org.springframework.kafka.core.KafkaTemplate"])
    @ConditionalOnProperty(prefix = "yudao.websocket", name = ["sender-type"], havingValue = "kafka")
    class KafkaWebSocketMessageSenderConfiguration {
        @Bean
        fun kafkaWebSocketMessageSender(
            sessionManager: WebSocketSessionManager,
            kafkaTemplate: KafkaTemplate<Any, Any>,
            @Value("\${yudao.websocket.sender-kafka.topic}") topic: String,
        ): KafkaWebSocketMessageSender =
            KafkaWebSocketMessageSender(sessionManager, kafkaTemplate, topic)

        @Bean
        fun kafkaWebSocketMessageConsumer(
            sender: KafkaWebSocketMessageSender,
        ): KafkaWebSocketMessageConsumer = KafkaWebSocketMessageConsumer(sender)
    }
}
