package im.hikaru.ruoyi.module.pay.job.notify

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJob
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
import org.springframework.stereotype.Component

@Component
class PayNotifyJob(
    private val payNotifyService: PayNotifyService,
) : JobHandler {
    @TenantJob
    override fun execute(param: String): String {
        val count = payNotifyService.executeNotify()
        return "执行支付通知 $count 个"
    }
}
