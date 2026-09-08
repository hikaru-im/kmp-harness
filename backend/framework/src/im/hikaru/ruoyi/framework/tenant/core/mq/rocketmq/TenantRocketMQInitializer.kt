package im.hikaru.ruoyi.framework.tenant.core.mq.rocketmq

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer
import org.springframework.beans.factory.config.BeanPostProcessor

class TenantRocketMQInitializer : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        when (bean) {
            is DefaultRocketMQListenerContainer -> initTenantConsumer(bean.consumer)
            is RocketMQTemplate -> initTenantProducer(bean.producer)
        }
        return bean
    }

    private fun initTenantProducer(producer: DefaultMQProducer?) {
        producer?.defaultMQProducerImpl?.registerSendMessageHook(TenantRocketMQSendMessageHook())
    }

    private fun initTenantConsumer(consumer: DefaultMQPushConsumer?) {
        consumer?.defaultMQPushConsumerImpl?.registerConsumeMessageHook(TenantRocketMQConsumeMessageHook())
    }
}
