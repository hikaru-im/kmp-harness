package im.hikaru.ruoyi.module.system.dal.mysql.sms

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.smallIntBoolean
import im.hikaru.ruoyi.module.system.dal.mysql.jsonAnyMap
import im.hikaru.ruoyi.module.system.dal.mysql.jsonStringList
import org.jetbrains.exposed.v1.datetime.datetime

object SmsChannelTable : BaseTable("system_sms_channel") {
    val id = long("id").autoIncrement("system_sms_channel_seq")
    val signature = varchar("signature", 12)
    val code = varchar("code", 63)
    val status = integer("status")
    val remark = varchar("remark", 255).nullable()
    val apiKey = varchar("api_key", 128)
    val apiSecret = varchar("api_secret", 128).nullable()
    val callbackUrl = varchar("callback_url", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object SmsTemplateTable : BaseTable("system_sms_template") {
    val id = long("id").autoIncrement("system_sms_template_seq")
    val type = integer("type")
    val status = integer("status")
    val code = varchar("code", 63)
    val name = varchar("name", 63)
    val content = varchar("content", 255)
    val params = jsonStringList("params", 255)
    val remark = varchar("remark", 255).nullable()
    val apiTemplateId = varchar("api_template_id", 63)
    val channelId = long("channel_id")
    val channelCode = varchar("channel_code", 63)
    override val primaryKey = PrimaryKey(id)
}

object SmsLogTable : BaseTable("system_sms_log") {
    val id = long("id").autoIncrement("system_sms_log_seq")
    val channelId = long("channel_id")
    val channelCode = varchar("channel_code", 63)
    val templateId = long("template_id")
    val templateCode = varchar("template_code", 63)
    val templateType = integer("template_type")
    val templateContent = varchar("template_content", 255)
    val templateParams = jsonAnyMap("template_params", 255)
    val apiTemplateId = varchar("api_template_id", 63)
    val mobile = varchar("mobile", 11)
    val userId = long("user_id").nullable()
    val userType = integer("user_type").nullable()
    val sendStatus = integer("send_status")
    val sendTime = datetime("send_time").nullable()
    val apiSendCode = varchar("api_send_code", 63).nullable()
    val apiSendMsg = varchar("api_send_msg", 255).nullable()
    val apiRequestId = varchar("api_request_id", 255).nullable()
    val apiSerialNo = varchar("api_serial_no", 255).nullable()
    val receiveStatus = integer("receive_status")
    val receiveTime = datetime("receive_time").nullable()
    val apiReceiveCode = varchar("api_receive_code", 63).nullable()
    val apiReceiveMsg = varchar("api_receive_msg", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object SmsCodeTable : BaseTable("system_sms_code") {
    val id = long("id").autoIncrement("system_sms_code_seq")
    val mobile = varchar("mobile", 11)
    val code = varchar("code", 6)
    val createIp = varchar("create_ip", 15)
    val scene = integer("scene")
    val todayIndex = integer("today_index")
    val used = smallIntBoolean("used")
    val usedTime = datetime("used_time").nullable()
    val usedIp = varchar("used_ip", 255).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
