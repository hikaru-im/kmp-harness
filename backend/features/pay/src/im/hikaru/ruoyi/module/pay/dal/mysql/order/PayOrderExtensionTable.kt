package im.hikaru.ruoyi.module.pay.dal.mysql.order

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.jsonStringMap

object PayOrderExtensionTable : BaseTable("pay_order_extension") {
    val id = long("id").autoIncrement("pay_order_extension_seq")
    val no = varchar("no", 128).nullable()
    val orderId = long("order_id").nullable()
    val channelId = long("channel_id").nullable()
    val channelCode = varchar("channel_code", 128).nullable()
    val userIp = varchar("user_ip", 255).nullable()
    val status = integer("status").nullable()
    val channelExtras = jsonStringMap("channel_extras", 4096).nullable()
    val channelErrorCode = varchar("channel_error_code", 128).nullable()
    val channelErrorMsg = varchar("channel_error_msg", 255).nullable()
    val channelNotifyData = varchar("channel_notify_data", 4096).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
