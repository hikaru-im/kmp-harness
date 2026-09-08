package im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel

import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.hibernate.validator.constraints.URL
import java.time.LocalDateTime

class SmsChannelPageReqVO : PageParam() {
    var status: Int? = null
    var signature: String? = null
    var createTime: List<KotlinLocalDateTime>? = null
}

class SmsChannelSaveReqVO {
    var id: Long? = null

    @field:NotBlank(message = "SMS signature must not be blank")
    var signature: String? = null

    @field:NotBlank(message = "SMS channel code must not be blank")
    var code: String? = null

    @field:NotNull(message = "SMS channel status must not be null")
    var status: Int? = null

    var remark: String? = null

    @field:NotBlank(message = "SMS API key must not be blank")
    var apiKey: String? = null

    var apiSecret: String? = null

    @field:URL(message = "Callback URL format is invalid")
    var callbackUrl: String? = null
}

open class SmsChannelSimpleRespVO {
    @ExcelProperty("Id")
    var id: Long? = null
    @ExcelProperty("Signature")
    var signature: String? = null
    @ExcelProperty("Code")
    var code: String? = null
}

@ExcelIgnoreUnannotated
class SmsChannelRespVO : SmsChannelSimpleRespVO() {
    @ExcelProperty("Status")
    var status: Int? = null
    @ExcelProperty("Remark")
    var remark: String? = null
    // Credentials are intentionally excluded from spreadsheet exports.
    var apiKey: String? = null
    var apiSecret: String? = null
    @ExcelProperty("Callback URL")
    var callbackUrl: String? = null
    @ExcelProperty("Created at")
    var createTime: LocalDateTime? = null
}
