package im.hikaru.ruoyi.module.pay.service.notify

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayOrderNotifyReqDTO
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayRefundNotifyReqDTO
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayTransferNotifyReqDTO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyTaskDO
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyStatusEnum
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import java.time.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PayNotifyServiceImplTest {
    @Test
    fun `builds the reference callback DTOs`() {
        val order = buildNotifyRequest(task(PayNotifyTypeEnum.ORDER).apply {
            merchantOrderId = "order-1"
        }) as PayOrderNotifyReqDTO
        assertThat(order.merchantOrderId).isEqualTo("order-1")
        assertThat(order.payOrderId).isEqualTo(42L)

        val refund = buildNotifyRequest(task(PayNotifyTypeEnum.REFUND).apply {
            merchantOrderId = "order-1"
            merchantRefundId = "refund-1"
        }) as PayRefundNotifyReqDTO
        assertThat(refund.merchantOrderId).isEqualTo("order-1")
        assertThat(refund.merchantRefundId).isEqualTo("refund-1")
        assertThat(refund.payRefundId).isEqualTo(42L)

        val transfer = buildNotifyRequest(task(PayNotifyTypeEnum.TRANSFER).apply {
            merchantTransferId = "transfer-1"
        }) as PayTransferNotifyReqDTO
        assertThat(transfer.merchantTransferId).isEqualTo("transfer-1")
        assertThat(transfer.payTransferId).isEqualTo(42L)
    }

    @Test
    fun `business failures and request failures keep their distinct retry states`() {
        val now = LocalDateTime.of(2026, 7, 22, 12, 0)
        val businessFailure = calculateNotifyResult(
            task = task(PayNotifyTypeEnum.ORDER),
            invokeResult = CommonResult<Any?>(400, "failed"),
            invokeException = null,
            lastExecuteTime = now,
        )
        assertThat(businessFailure.status).isEqualTo(PayNotifyStatusEnum.REQUEST_SUCCESS.status)
        assertThat(businessFailure.notifyTimes).isEqualTo(1)
        assertThat(businessFailure.nextNotifyTime)
            .isEqualTo(now.plusSeconds(15).toKotlinLocalDateTime())

        val requestFailure = calculateNotifyResult(
            task = task(PayNotifyTypeEnum.ORDER),
            invokeResult = null,
            invokeException = IllegalStateException("network error"),
            lastExecuteTime = now,
        )
        assertThat(requestFailure.status).isEqualTo(PayNotifyStatusEnum.REQUEST_FAILURE.status)
        assertThat(requestFailure.nextNotifyTime)
            .isEqualTo(now.plusSeconds(15).toKotlinLocalDateTime())
    }

    @Test
    fun `success and the final failed attempt stop retrying`() {
        val now = LocalDateTime.of(2026, 7, 22, 12, 0)
        val success = calculateNotifyResult(
            task = task(PayNotifyTypeEnum.ORDER),
            invokeResult = CommonResult.success("ok"),
            invokeException = null,
            lastExecuteTime = now,
        )
        assertThat(success.status).isEqualTo(PayNotifyStatusEnum.SUCCESS.status)
        assertThat(success.nextNotifyTime).isNull()

        val finalFailureTask = task(PayNotifyTypeEnum.ORDER).apply { notifyTimes = 8 }
        val finalFailure = calculateNotifyResult(
            task = finalFailureTask,
            invokeResult = CommonResult<Any?>(400, "failed"),
            invokeException = null,
            lastExecuteTime = now,
        )
        assertThat(finalFailure.status).isEqualTo(PayNotifyStatusEnum.FAILURE.status)
        assertThat(finalFailure.notifyTimes).isEqualTo(9)
        assertThat(finalFailure.nextNotifyTime).isNull()
    }

    @Test
    fun `rejects unknown callback types`() {
        assertThatThrownBy { buildNotifyRequest(PayNotifyTaskDO().apply { type = -1 }) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun task(type: PayNotifyTypeEnum): PayNotifyTaskDO = PayNotifyTaskDO().apply {
        this.type = type.type
        dataId = 42L
        notifyTimes = 0
        maxNotifyTimes = PayNotifyTaskDO.DEFAULT_MAX_NOTIFY_TIMES
    }
}
