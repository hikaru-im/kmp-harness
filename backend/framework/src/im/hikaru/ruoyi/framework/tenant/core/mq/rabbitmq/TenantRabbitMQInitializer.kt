package im.hikaru.ruoyi.framework.tenant.core.mq.rabbitmq

import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor

/**
 * 多租户的 RabbitMQ 初始化器 (迁移自 Java)
 *
 * @author 芋道源码
 */
class TenantRabbitMQInitializer : BeanPostProcessor {

    @Throws(BeansException::class)
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is RabbitTemplate) {
            bean.addBeforePublishPostProcessors(TenantRabbitMQMessagePostProcessor())
        } else if (bean is AbstractRabbitListenerContainerFactory<*>) {
            bean.setAdviceChain(*bean.adviceChain, TenantRabbitMQMessageInterceptor())
        }
        return bean
    }
}
