package im.hikaru.ruoyi.module.system.controller.admin.mail.vo.log

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime

class MailLogPageReqVO : PageParam() {
    var userId: Long? = null
    var userType: Int? = null
    var toMail: String? = null
    var accountId: Long? = null
    var templateId: Long? = null
    var sendStatus: Int? = null
    var sendTime: List<KotlinLocalDateTime>? = null
}

class MailLogRespVO {
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
    var createTime: LocalDateTime? = null
}
