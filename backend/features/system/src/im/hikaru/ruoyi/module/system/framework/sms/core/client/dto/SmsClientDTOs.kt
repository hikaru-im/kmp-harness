package im.hikaru.ruoyi.module.system.framework.sms.core.client.dto

import kotlinx.datetime.LocalDateTime

data class SmsSendRespDTO(
    var success: Boolean = false,
    var apiRequestId: String? = null,
    var serialNo: String? = null,
    var apiCode: String? = null,
    var apiMsg: String? = null,
)

data class SmsReceiveRespDTO(
    var success: Boolean = false,
    var errorCode: String? = null,
    var errorMsg: String? = null,
    var mobile: String? = null,
    var receiveTime: LocalDateTime? = null,
    var serialNo: String? = null,
    var logId: Long? = null,
)

data class SmsTemplateRespDTO(
    var id: String? = null,
    var content: String? = null,
    var auditStatus: Int? = null,
    var auditReason: String? = null,
)
