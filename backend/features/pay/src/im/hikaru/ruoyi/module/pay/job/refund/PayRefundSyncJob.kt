package im.hikaru.ruoyi.module.pay.job.refund

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJob
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import org.springframework.stereotype.Component

@Component
class PayRefundSyncJob(
    private val refundService: PayRefundService,
) : JobHandler {
    @TenantJob
    override fun execute(param: String): String {
        val count = refundService.syncRefund()
        return "同步退款订单 $count 个"
    }
}
