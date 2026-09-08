package im.hikaru.ruoyi.module.pay.framework.job.config

import java.util.concurrent.ThreadPoolExecutor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration(proxyBeanMethods = false)
class PayJobConfiguration {
    @Bean(NOTIFY_THREAD_POOL_TASK_EXECUTOR)
    fun notifyThreadPoolTaskExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 8
            maxPoolSize = 16
            keepAliveSeconds = 60
            queueCapacity = 100
            setThreadNamePrefix("notify-task-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }

    companion object {
        const val NOTIFY_THREAD_POOL_TASK_EXECUTOR = "NOTIFY_THREAD_POOL_TASK_EXECUTOR"
    }
}
