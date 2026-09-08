package im.hikaru.ruoyi.module.system.dal.dataobject.sms

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

abstract class SmsBaseEntity : BaseEntity {
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}

class SmsChannelDO : SmsBaseEntity() {
    var id: Long? = null
    var signature: String? = null
    var code: String? = null
    var status: Int? = null
    var remark: String? = null
    var apiKey: String? = null
    var apiSecret: String? = null
    var callbackUrl: String? = null
}

class SmsTemplateDO : SmsBaseEntity() {
    var id: Long? = null
    var type: Int? = null
    var status: Int? = null
    var code: String? = null
    var name: String? = null
    var content: String? = null
    var params: List<String>? = null
    var remark: String? = null
    var apiTemplateId: String? = null
    var channelId: Long? = null
    var channelCode: String? = null
}

class SmsLogDO : SmsBaseEntity() {
    var id: Long? = null
    var channelId: Long? = null
    var channelCode: String? = null
    var templateId: Long? = null
    var templateCode: String? = null
    var templateType: Int? = null
    var templateContent: String? = null
    var templateParams: Map<String, Any?>? = null
    var apiTemplateId: String? = null
    var mobile: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var sendStatus: Int? = null
    var sendTime: LocalDateTime? = null
    var apiSendCode: String? = null
    var apiSendMsg: String? = null
    var apiRequestId: String? = null
    var apiSerialNo: String? = null
    var receiveStatus: Int? = null
    var receiveTime: LocalDateTime? = null
    var apiReceiveCode: String? = null
    var apiReceiveMsg: String? = null
}

class SmsCodeDO : SmsBaseEntity(), TenantBaseDO {
    var id: Long? = null
    var mobile: String? = null
    var code: String? = null
    var scene: Int? = null
    var createIp: String? = null
    var todayIndex: Int? = null
    var used: Boolean? = null
    var usedTime: LocalDateTime? = null
    var usedIp: String? = null
    override var tenantId: Long? = null
}
