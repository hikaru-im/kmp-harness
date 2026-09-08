package im.hikaru.ruoyi.module.system.dal.dataobject.mail

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

abstract class MailBaseEntity : BaseEntity {
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}

class MailAccountDO : MailBaseEntity() {
    var id: Long? = null
    var mail: String? = null
    var username: String? = null
    var password: String? = null
    var host: String? = null
    var port: Int? = null
    var sslEnable: Boolean? = null
    var starttlsEnable: Boolean? = null
}

class MailTemplateDO : MailBaseEntity() {
    var id: Long? = null
    var name: String? = null
    var code: String? = null
    var accountId: Long? = null
    var nickname: String? = null
    var title: String? = null
    var content: String? = null
    var params: List<String>? = null
    var status: Int? = null
    var remark: String? = null
}

class MailLogDO : MailBaseEntity() {
    var id: Long? = null
    var userId: Long? = null
    var userType: Int? = null
    var toMails: List<String>? = null
    var ccMails: List<String>? = null
    var bccMails: List<String>? = null
    var accountId: Long? = null
    var fromMail: String? = null
    var templateId: Long? = null
    var templateCode: String? = null
    var templateNickname: String? = null
    var templateTitle: String? = null
    var templateContent: String? = null
    var templateParams: Map<String, Any?>? = null
    var sendStatus: Int? = null
    var sendTime: LocalDateTime? = null
    var sendMessageId: String? = null
    var sendException: String? = null
}
