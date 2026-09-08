package im.hikaru.ruoyi.module.pay.dal.mysql.refund

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object PayRefundTable : BaseTable("pay_refund") {
    val id = long("id").autoIncrement("pay_refund_seq")
    val no = varchar("no", 128).nullable()
    val appId = long("app_id").nullable()
    val channelId = long("channel_id").nullable()
    val channelCode = varchar("channel_code", 128).nullable()
    val orderId = long("order_id").nullable()
    val orderNo = varchar("order_no", 128).nullable()
    val userId = long("user_id").nullable()
    val userType = integer("user_type").nullable()
    val merchantOrderId = varchar("merchant_order_id", 128).nullable()
    val merchantRefundId = varchar("merchant_refund_id", 128).nullable()
    val notifyUrl = varchar("notify_url", 2048).nullable()
    val status = integer("status").nullable()
    val payPrice = integer("pay_price").nullable()
    val refundPrice = integer("refund_price").nullable()
    val reason = varchar("reason", 255).nullable()
    val userIp = varchar("user_ip", 255).nullable()
    val channelOrderNo = varchar("channel_order_no", 128).nullable()
    val channelRefundNo = varchar("channel_refund_no", 128).nullable()
    val successTime = datetime("success_time").nullable()
    val channelErrorCode = varchar("channel_error_code", 128).nullable()
    val channelErrorMsg = varchar("channel_error_msg", 255).nullable()
    val channelNotifyData = varchar("channel_notify_data", 4096).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
