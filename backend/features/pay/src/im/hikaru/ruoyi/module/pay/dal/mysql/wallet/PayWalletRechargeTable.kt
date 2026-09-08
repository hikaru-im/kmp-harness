package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.smallIntBoolean
import org.jetbrains.exposed.v1.datetime.datetime

object PayWalletRechargeTable : BaseTable("pay_wallet_recharge") {
    val id = long("id").autoIncrement("pay_wallet_recharge_seq")
    val walletId = long("wallet_id").nullable()
    val totalPrice = integer("total_price").nullable()
    val payPrice = integer("pay_price").nullable()
    val bonusPrice = integer("bonus_price").nullable()
    val packageId = long("package_id").nullable()
    val payStatus = smallIntBoolean("pay_status").nullable()
    val payOrderId = long("pay_order_id").nullable()
    val payChannelCode = varchar("pay_channel_code", 128).nullable()
    val payTime = datetime("pay_time").nullable()
    val payRefundId = long("pay_refund_id").nullable()
    val refundTotalPrice = integer("refund_total_price").nullable()
    val refundPayPrice = integer("refund_pay_price").nullable()
    val refundBonusPrice = integer("refund_bonus_price").nullable()
    val refundTime = datetime("refund_time").nullable()
    val refundStatus = integer("refund_status").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
