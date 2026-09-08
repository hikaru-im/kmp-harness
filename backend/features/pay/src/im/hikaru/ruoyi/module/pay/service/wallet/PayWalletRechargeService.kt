package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletRechargeCreateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargeDO

interface PayWalletRechargeService {
    fun createWalletRecharge(userId: Long, userType: Int, userIp: String, createReqVO: AppPayWalletRechargeCreateReqVO): PayWalletRechargeDO
    fun getWalletRechargePackagePage(userId: Long, userType: Int, pageReqVO: PageParam, payStatus: Boolean): PageResult<PayWalletRechargeDO>
    fun updateWalletRechargerPaid(id: Long, payOrderId: Long): Unit
    fun refundWalletRecharge(id: Long, userIp: String): Unit
    fun updateWalletRechargeRefunded(id: Long, refundId: String, payRefundId: Long): Unit
}
