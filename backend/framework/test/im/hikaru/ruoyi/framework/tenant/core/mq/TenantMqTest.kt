package im.hikaru.ruoyi.framework.tenant.core.mq

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.mq.kafka.TenantKafkaEnvironmentPostProcessor
import im.hikaru.ruoyi.framework.tenant.core.mq.kafka.TenantKafkaConsumerInterceptor
import im.hikaru.ruoyi.framework.tenant.core.mq.kafka.TenantKafkaInitializer
import im.hikaru.ruoyi.framework.tenant.core.mq.kafka.TenantKafkaProducerInterceptor
import im.hikaru.ruoyi.framework.tenant.core.mq.rabbitmq.TenantRabbitMQInitializer
import im.hikaru.ruoyi.framework.tenant.core.mq.rabbitmq.TenantRabbitMQMessageInterceptor
import im.hikaru.ruoyi.framework.tenant.core.mq.rocketmq.TenantRocketMQConsumeMessageHook
import im.hikaru.ruoyi.framework.tenant.core.mq.rocketmq.TenantRocketMQSendMessageHook
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.aopalliance.intercept.MethodInvocation
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.rocketmq.client.hook.ConsumeMessageContext
import org.apache.rocketmq.client.hook.SendMessageContext
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.common.message.MessageExt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.amqp.core.Message as RabbitMessage
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.boot.SpringApplication
import org.springframework.core.env.StandardEnvironment
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory

class TenantMqTest {

    @AfterEach
    fun cleanUp() {
        TenantContextHolder.clear()
        System.clearProperty(TenantKafkaEnvironmentPostProcessor.PROPERTY_KEY_INTERCEPTOR_CLASSES)
    }

    @Test
    fun `kafka integration registers interceptor and adds tenant header`() {
        val environment = StandardEnvironment()
        TenantKafkaEnvironmentPostProcessor().postProcessEnvironment(
            environment,
            SpringApplication(Any::class.java),
        )
        assertEquals(
            TenantKafkaProducerInterceptor::class.java.name,
            environment.getProperty(TenantKafkaEnvironmentPostProcessor.PROPERTY_KEY_INTERCEPTOR_CLASSES),
        )

        TenantContextHolder.setTenantId(42L)
        val record = ProducerRecord<Any, Any>("tenant-topic", "payload")
        TenantKafkaProducerInterceptor().onSend(record)

        assertArrayEquals(
            "42".toByteArray(),
            record.headers().lastHeader(WebFrameworkUtils.HEADER_TENANT_ID).value(),
        )
    }

    @Test
    fun `kafka consumer interceptor scopes tenant context`() {
        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        TenantKafkaInitializer().postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")
        val interceptor = requireNotNull(factory.recordInterceptor)
        assertTrue(interceptor is TenantKafkaConsumerInterceptor)

        val record = ConsumerRecord<Any, Any>("tenant-topic", 0, 1L, "key", "payload").apply {
            headers().add(WebFrameworkUtils.HEADER_TENANT_ID, "42".toByteArray())
        }
        @Suppress("UNCHECKED_CAST")
        val consumer = mock(Consumer::class.java) as Consumer<Any, Any>
        TenantContextHolder.setTenantId(7L)
        assertSame(record, interceptor.intercept(record, consumer))
        assertEquals(42L, TenantContextHolder.getTenantId())

        interceptor.afterRecord(record, consumer)
        assertEquals(7L, TenantContextHolder.getTenantId())
    }

    @Test
    fun `rabbit consumer advice scopes tenant context and preserves existing advice`() {
        val existing = mock(org.aopalliance.aop.Advice::class.java)
        val factory = SimpleRabbitListenerContainerFactory().apply { setAdviceChain(existing) }
        TenantRabbitMQInitializer().postProcessAfterInitialization(factory, "rabbitListenerContainerFactory")
        val adviceChain = requireNotNull(factory.adviceChain)
        assertSame(existing, adviceChain.first())
        val interceptor = adviceChain.last() as TenantRabbitMQMessageInterceptor

        val message = RabbitMessage(ByteArray(0), MessageProperties().apply {
            headers[WebFrameworkUtils.HEADER_TENANT_ID] = 42L
        })
        val invocation = mock(MethodInvocation::class.java)
        `when`(invocation.arguments).thenReturn(arrayOf(message))
        `when`(invocation.proceed()).thenAnswer {
            assertEquals(42L, TenantContextHolder.getTenantId())
            "handled"
        }

        TenantContextHolder.setTenantId(7L)
        assertEquals("handled", interceptor.invoke(invocation))
        assertEquals(7L, TenantContextHolder.getTenantId())
        verify(invocation).proceed()
    }

    @Test
    fun `rocketmq send and consume hooks propagate tenant context`() {
        TenantContextHolder.setTenantId(84L)
        val message = Message("tenant-topic", "payload".toByteArray())
        val sendContext = SendMessageContext().apply { this.message = message }
        TenantRocketMQSendMessageHook().sendMessageBefore(sendContext)
        assertEquals("84", message.getUserProperty(WebFrameworkUtils.HEADER_TENANT_ID))

        TenantContextHolder.clear()
        val received = MessageExt().apply {
            putUserProperty(WebFrameworkUtils.HEADER_TENANT_ID, "84")
        }
        val consumeContext = ConsumeMessageContext().apply { msgList = mutableListOf(received) }
        val hook = TenantRocketMQConsumeMessageHook()
        hook.consumeMessageBefore(consumeContext)
        assertEquals(84L, TenantContextHolder.getTenantId())

        hook.consumeMessageAfter(consumeContext)
        assertNull(TenantContextHolder.getTenantId())
    }
}
