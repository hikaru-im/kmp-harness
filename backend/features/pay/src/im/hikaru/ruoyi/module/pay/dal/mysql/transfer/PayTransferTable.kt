package im.hikaru.ruoyi.module.pay.dal.mysql.transfer

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.jsonStringMap
import org.jetbrains.exposed.v1.datetime.datetime

object PayTransferTable : BaseTable("pay_transfer") {
    val id = long("id").autoIncrement("pay_transfer_seq")
    val no = varchar("no", 128).nullable()
    val appId = long("app_id").nullable()
    val channelId = long("channel_id").nullable()
    val channelCode = varchar("channel_code", 128).nullable()
    val userId = long("user_id").nullable()
    val userType = integer("user_type").nullable()
    val merchantTransferId = varchar("merchant_transfer_id", 128).nullable()
    val subject = varchar("subject", 255).nullable()
    val price = integer("price").nullable()
    val userAccount = varchar("user_account", 255).nullable()
    val userName = varchar("user_name", 255).nullable()
    val status = integer("status").nullable()
    val successTime = datetime("success_time").nullable()
    val notifyUrl = varchar("notify_url", 2048).nullable()
    val userIp = varchar("user_ip", 255).nullable()
    val channelExtras = jsonStringMap("channel_extras", 4096).nullable()
    val channelTransferNo = varchar("channel_transfer_no", 128).nullable()
    val channelErrorCode = varchar("channel_error_code", 128).nullable()
    val channelErrorMsg = varchar("channel_error_msg", 255).nullable()
    val channelNotifyData = varchar("channel_notify_data", 4096).nullable()
    val channelPackageInfo = varchar("channel_package_info", 4096).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
