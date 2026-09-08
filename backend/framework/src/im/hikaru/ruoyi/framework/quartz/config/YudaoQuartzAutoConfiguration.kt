package im.hikaru.ruoyi.framework.quartz.config

import im.hikaru.ruoyi.framework.quartz.core.scheduler.SchedulerManager
import org.quartz.Scheduler
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.Optional

/**
 * 定时任务 Configuration (迁移自 Java, 去 Lombok)
 */
@AutoConfiguration
@EnableScheduling // 开启 Spring 自带的定时任务
class YudaoQuartzAutoConfiguration {

    @Bean
    fun schedulerManager(scheduler: Optional<Scheduler>): SchedulerManager {
        if (scheduler.isEmpty) {
            log.info("[定时任务 - 已禁用][参考 https://doc.iocoder.cn/job/ 开启]")
            return SchedulerManager(null)
        }
        return SchedulerManager(scheduler.get())
    }

    companion object {
        private val log = LoggerFactory.getLogger(YudaoQuartzAutoConfiguration::class.java)
    }
}
