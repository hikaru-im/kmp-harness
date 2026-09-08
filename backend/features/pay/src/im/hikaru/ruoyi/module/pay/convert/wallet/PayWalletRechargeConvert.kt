package im.hikaru.ruoyi.module.pay.convert.wallet

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletRechargeCreateRespVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletRechargeRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargeDO
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import kotlinx.datetime.toJavaLocalDateTime

object PayWalletRechargeConvert {
    fun create(walletId: Long, payPrice: Int, bonusPrice: Int, packageId: Long?): PayWalletRechargeDO =
        PayWalletRechargeDO().apply {
            this.walletId = walletId
            totalPrice = payPrice + bonusPrice
            this.payPrice = payPrice
            this.bonusPrice = bonusPrice
            this.packageId = packageId
            payStatus = false
        }

    fun createResponse(bean: PayWalletRechargeDO) = AppPayWalletRechargeCreateRespVO().apply {
        id = bean.id
        payOrderId = bean.payOrderId
    }

    fun page(page: PageResult<PayWalletRechargeDO>, orders: Map<Long, PayOrderDO>): PageResult<AppPayWalletRechargeRespVO> =
        PageResult(page.total, page.list.map { bean ->
            AppPayWalletRechargeRespVO().apply {
                id = bean.id
                totalPrice = bean.totalPrice
                payPrice = bean.payPrice
                bonusPrice = bean.bonusPrice
                payChannelCode = bean.payChannelCode
                payChannelName = PayChannelEnum.getByCode(bean.payChannelCode)?.displayName
                payOrderId = bean.payOrderId
                payOrderChannelOrderNo = bean.payOrderId?.let(orders::get)?.channelOrderNo
                payTime = bean.payTime?.toJavaLocalDateTime()
                refundStatus = bean.refundStatus
            }
        })
}
