package im.hikaru.ruoyi.module.pay.job.order

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJob
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import org.springframework.stereotype.Component

@Component
class PayOrderExpireJob(
    private val orderService: PayOrderService,
) : JobHandler {
    @TenantJob
    override fun execute(param: String): String {
        val count = orderService.expireOrder()
        return "支付过期 $count 个"
    }
}
