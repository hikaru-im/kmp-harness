package im.hikaru.ruoyi.module.pay.dal.mysql.order

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object PayOrderTable : BaseTable("pay_order") {
    val id = long("id").autoIncrement("pay_order_seq")
    val appId = long("app_id").nullable()
    val channelId = long("channel_id").nullable()
    val channelCode = varchar("channel_code", 128).nullable()
    val userId = long("user_id").nullable()
    val userType = integer("user_type").nullable()
    val merchantOrderId = varchar("merchant_order_id", 128).nullable()
    val subject = varchar("subject", 255).nullable()
    val body = varchar("body", 2048).nullable()
    val notifyUrl = varchar("notify_url", 2048).nullable()
    val price = integer("price").nullable()
    val channelFeeRate = double("channel_fee_rate").nullable()
    val channelFeePrice = integer("channel_fee_price").nullable()
    val status = integer("status").nullable()
    val userIp = varchar("user_ip", 255).nullable()
    val expireTime = datetime("expire_time").nullable()
    val successTime = datetime("success_time").nullable()
    val extensionId = long("extension_id").nullable()
    val no = varchar("no", 128).nullable()
    val refundPrice = integer("refund_price").nullable()
    val channelUserId = varchar("channel_user_id", 128).nullable()
    val channelOrderNo = varchar("channel_order_no", 128).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
