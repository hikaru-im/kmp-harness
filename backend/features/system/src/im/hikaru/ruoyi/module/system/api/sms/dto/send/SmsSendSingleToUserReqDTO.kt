package im.hikaru.ruoyi.module.system.api.sms.dto.send

import im.hikaru.ruoyi.framework.common.validation.Mobile
import jakarta.validation.constraints.NotEmpty

class SmsSendSingleToUserReqDTO {
    var userId: Long? = null
    @field:Mobile
    var mobile: String? = null

    @field:NotEmpty(message = "SMS template code must not be empty")
    var templateCode: String? = null
    var templateParams: Map<String, Any?>? = null
}
