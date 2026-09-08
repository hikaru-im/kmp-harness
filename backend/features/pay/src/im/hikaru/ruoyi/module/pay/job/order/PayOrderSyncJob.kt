package im.hikaru.ruoyi.module.pay.job.order

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJob
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import java.time.Duration
import java.time.LocalDateTime
import org.springframework.stereotype.Component

@Component
class PayOrderSyncJob(
    private val orderService: PayOrderService,
) : JobHandler {
    @TenantJob
    override fun execute(param: String): String {
        val minCreateTime = LocalDateTime.now().minus(CREATE_TIME_DURATION_BEFORE)
        val count = orderService.syncOrder(minCreateTime)
        return "同步支付订单 $count 个"
    }

    companion object {
        private val CREATE_TIME_DURATION_BEFORE: Duration = Duration.ofMinutes(10)
    }
}
