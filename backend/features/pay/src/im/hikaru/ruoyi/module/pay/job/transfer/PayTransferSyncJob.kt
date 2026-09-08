package im.hikaru.ruoyi.module.pay.job.transfer

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJob
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
import org.springframework.stereotype.Component

@Component
class PayTransferSyncJob(
    private val transferService: PayTransferService,
) : JobHandler {
    @TenantJob
    override fun execute(param: String): String {
        val count = transferService.syncTransfer()
        return "同步转账订单 $count 个"
    }
}
