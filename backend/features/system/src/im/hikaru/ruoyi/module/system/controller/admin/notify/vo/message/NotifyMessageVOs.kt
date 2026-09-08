package im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime

class NotifyMessagePageReqVO : PageParam() {
    var userId: Long? = null
    var userType: Int? = null
    var templateCode: String? = null
    var templateType: Int? = null
    var createTime: List<KotlinLocalDateTime>? = null
}

class NotifyMessageMyPageReqVO : PageParam() {
    var readStatus: Boolean? = null
    var createTime: List<KotlinLocalDateTime>? = null
}

class NotifyMessageRespVO {
    var id: Long? = null
    var userId: Long? = null
    var userType: Int? = null
    var templateId: Long? = null
    var templateCode: String? = null
    var templateNickname: String? = null
    var templateContent: String? = null
    var templateType: Int? = null
    var templateParams: Map<String, Any?>? = null
    var readStatus: Boolean? = null
    var readTime: LocalDateTime? = null
    var createTime: LocalDateTime? = null
}
