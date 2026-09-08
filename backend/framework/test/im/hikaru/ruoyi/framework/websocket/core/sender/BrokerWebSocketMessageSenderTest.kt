package im.hikaru.ruoyi.framework.websocket.core.sender

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.sender.kafka.KafkaWebSocketMessage
import im.hikaru.ruoyi.framework.websocket.core.sender.kafka.KafkaWebSocketMessageConsumer
import im.hikaru.ruoyi.framework.websocket.core.sender.kafka.KafkaWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq.RabbitMQWebSocketMessage
import im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq.RabbitMQWebSocketMessageConsumer
import im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq.RabbitMQWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq.RocketMQWebSocketMessage
import im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq.RocketMQWebSocketMessageConsumer
import im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq.RocketMQWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.CompletableFuture

class BrokerWebSocketMessageSenderTest {

    @AfterEach
    fun tearDown() = TenantContextHolder.clear()

    @Test
    fun `broker senders publish the websocket routing envelope`() {
        val sessionManager = mock(WebSocketSessionManager::class.java)
        TenantContextHolder.setTenantId(42L)

        val kafkaTemplate = kafkaTemplate()
        @Suppress("UNCHECKED_CAST")
        val kafkaResult = mock(SendResult::class.java) as SendResult<Any, Any>
        `when`(kafkaTemplate.send(eq("ws.kafka"), any())).thenReturn(CompletableFuture.completedFuture(kafkaResult))
        KafkaWebSocketMessageSender(sessionManager, kafkaTemplate, "ws.kafka")
            .send(1, 7L, "notice", "kafka")
        val kafkaCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(kafkaTemplate).send(eq("ws.kafka"), kafkaCaptor.capture())
        assertEnvelope(kafkaCaptor.value as KafkaWebSocketMessage, "kafka")

        val rabbitTemplate = mock(RabbitTemplate::class.java)
        RabbitMQWebSocketMessageSender(sessionManager, rabbitTemplate, TopicExchange("ws.rabbit"))
            .send(1, 7L, "notice", "rabbit")
        val rabbitCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(rabbitTemplate).convertAndSend(eq("ws.rabbit"), eq(null), rabbitCaptor.capture())
        assertEnvelope(rabbitCaptor.value as RabbitMQWebSocketMessage, "rabbit")

        val rocketTemplate = mock(RocketMQTemplate::class.java)
        RocketMQWebSocketMessageSender(sessionManager, rocketTemplate, "ws.rocket")
            .send(1, 7L, "notice", "rocket")
        val rocketCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(rocketTemplate).syncSend(eq("ws.rocket"), rocketCaptor.capture())
        assertEnvelope(rocketCaptor.value as RocketMQWebSocketMessage, "rocket")
    }

    @Test
    fun `broker consumers deliver received messages to the local session`() {
        val sessionManager = mock(WebSocketSessionManager::class.java)
        val session = mock(WebSocketSession::class.java)
        `when`(sessionManager.getSession("session-1")).thenReturn(session)
        `when`(session.isOpen).thenReturn(true)

        val kafkaTemplate = kafkaTemplate()
        KafkaWebSocketMessageConsumer(KafkaWebSocketMessageSender(sessionManager, kafkaTemplate, "unused"))
            .onMessage(KafkaWebSocketMessage().applyEnvelope("session-1", "kafka"))

        val rabbitTemplate = mock(RabbitTemplate::class.java)
        RabbitMQWebSocketMessageConsumer(
            RabbitMQWebSocketMessageSender(sessionManager, rabbitTemplate, TopicExchange("unused")),
        ).onMessage(RabbitMQWebSocketMessage().applyEnvelope("session-1", "rabbit"))

        val rocketTemplate = mock(RocketMQTemplate::class.java)
        RocketMQWebSocketMessageConsumer(RocketMQWebSocketMessageSender(sessionManager, rocketTemplate, "unused"))
            .onMessage(RocketMQWebSocketMessage().applyEnvelope("session-1", "rocket"))

        val messageCaptor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session, times(3)).sendMessage(messageCaptor.capture())
        assertThat(messageCaptor.allValues.map { it.payload })
            .allMatch { it.contains("\"type\":\"notice\"") }
        verifyNoInteractions(kafkaTemplate, rabbitTemplate, rocketTemplate)
        verify(session, never()).close()
    }

    @Test
    fun `broker consumers restore the envelope tenant for targeted delivery`() {
        val observedTenantIds = mutableListOf<Long?>()
        val sessionManager = mock(WebSocketSessionManager::class.java)
        `when`(sessionManager.getSessionList(1, 7L)).thenAnswer {
            observedTenantIds += TenantContextHolder.getTenantId()
            emptyList<WebSocketSession>()
        }

        val kafkaTemplate = kafkaTemplate()
        KafkaWebSocketMessageConsumer(KafkaWebSocketMessageSender(sessionManager, kafkaTemplate, "unused"))
            .onMessage(KafkaWebSocketMessage().applyTargetEnvelope())

        val rabbitTemplate = mock(RabbitTemplate::class.java)
        RabbitMQWebSocketMessageConsumer(
            RabbitMQWebSocketMessageSender(sessionManager, rabbitTemplate, TopicExchange("unused")),
        ).onMessage(RabbitMQWebSocketMessage().applyTargetEnvelope())

        val rocketTemplate = mock(RocketMQTemplate::class.java)
        RocketMQWebSocketMessageConsumer(RocketMQWebSocketMessageSender(sessionManager, rocketTemplate, "unused"))
            .onMessage(RocketMQWebSocketMessage().applyTargetEnvelope())

        assertThat(observedTenantIds).containsExactly(42L, 42L, 42L)
        assertThat(TenantContextHolder.getTenantId()).isNull()
        verifyNoInteractions(kafkaTemplate, rabbitTemplate, rocketTemplate)
    }

    private fun assertEnvelope(message: KafkaWebSocketMessage, content: String) {
        assertThat(message.tenantId).isEqualTo(42L)
        assertThat(message.userType).isEqualTo(1)
        assertThat(message.userId).isEqualTo(7L)
        assertThat(message.messageType).isEqualTo("notice")
        assertThat(message.messageContent).isEqualTo(content)
    }

    private fun assertEnvelope(message: RabbitMQWebSocketMessage, content: String) {
        assertThat(message.tenantId).isEqualTo(42L)
        assertThat(message.userType).isEqualTo(1)
        assertThat(message.userId).isEqualTo(7L)
        assertThat(message.messageType).isEqualTo("notice")
        assertThat(message.messageContent).isEqualTo(content)
    }

    private fun assertEnvelope(message: RocketMQWebSocketMessage, content: String) {
        assertThat(message.tenantId).isEqualTo(42L)
        assertThat(message.userType).isEqualTo(1)
        assertThat(message.userId).isEqualTo(7L)
        assertThat(message.messageType).isEqualTo("notice")
        assertThat(message.messageContent).isEqualTo(content)
    }

    private fun KafkaWebSocketMessage.applyEnvelope(sessionId: String, content: String) = apply {
        tenantId = 42L
        this.sessionId = sessionId
        messageType = "notice"
        messageContent = content
    }

    private fun RabbitMQWebSocketMessage.applyEnvelope(sessionId: String, content: String) = apply {
        tenantId = 42L
        this.sessionId = sessionId
        messageType = "notice"
        messageContent = content
    }

    private fun RocketMQWebSocketMessage.applyEnvelope(sessionId: String, content: String) = apply {
        tenantId = 42L
        this.sessionId = sessionId
        messageType = "notice"
        messageContent = content
    }

    private fun KafkaWebSocketMessage.applyTargetEnvelope() = apply {
        tenantId = 42L
        userType = 1
        userId = 7L
        messageType = "notice"
        messageContent = "kafka"
    }

    private fun RabbitMQWebSocketMessage.applyTargetEnvelope() = apply {
        tenantId = 42L
        userType = 1
        userId = 7L
        messageType = "notice"
        messageContent = "rabbit"
    }

    private fun RocketMQWebSocketMessage.applyTargetEnvelope() = apply {
        tenantId = 42L
        userType = 1
        userId = 7L
        messageType = "notice"
        messageContent = "rocket"
    }

    @Suppress("UNCHECKED_CAST")
    private fun kafkaTemplate(): KafkaTemplate<Any, Any> =
        mock(KafkaTemplate::class.java) as KafkaTemplate<Any, Any>
}
