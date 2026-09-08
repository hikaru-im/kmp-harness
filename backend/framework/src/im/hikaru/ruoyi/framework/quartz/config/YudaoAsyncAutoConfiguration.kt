package im.hikaru.ruoyi.framework.quartz.config

import com.alibaba.ttl.TtlRunnable
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 异步任务 Configuration (迁移自 Java)
 *
 * 通过 TTL (TransmittableThreadLocal) 装饰线程池，实现线程上下文传递。
 */
@AutoConfiguration
@EnableAsync
class YudaoAsyncAutoConfiguration {

    @Bean
    fun threadPoolTaskExecutorBeanPostProcessor(): BeanPostProcessor = object : BeanPostProcessor {
        @Throws(BeansException::class)
        override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
            // 处理 ThreadPoolTaskExecutor
            if (bean is ThreadPoolTaskExecutor) {
                bean.setTaskDecorator { runnable: Runnable -> TtlRunnable.get(runnable)!! }
                return bean
            }
            // 处理 SimpleAsyncTaskExecutor
            if (bean is SimpleAsyncTaskExecutor) {
                bean.setTaskDecorator { runnable: Runnable -> TtlRunnable.get(runnable)!! }
                return bean
            }
            return bean
        }
    }
}
