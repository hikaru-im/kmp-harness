package im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime

class SmsTemplatePageReqVO : PageParam() {
    var type: Int? = null
    var status: Int? = null
    var code: String? = null
    var content: String? = null
    var apiTemplateId: String? = null
    var channelId: Long? = null
    var createTime: List<KotlinLocalDateTime>? = null
}

class SmsTemplateSaveReqVO {
    var id: Long? = null

    @field:NotNull(message = "SMS template type must not be null")
    var type: Int? = null

    @field:NotNull(message = "SMS template status must not be null")
    var status: Int? = null

    @field:NotBlank(message = "SMS template code must not be blank")
    var code: String? = null

    @field:NotBlank(message = "SMS template name must not be blank")
    var name: String? = null

    @field:NotBlank(message = "SMS template content must not be blank")
    var content: String? = null

    var remark: String? = null

    @field:NotBlank(message = "SMS API template id must not be blank")
    var apiTemplateId: String? = null

    @field:NotNull(message = "SMS channel id must not be null")
    var channelId: Long? = null
}

open class SmsTemplateSimpleRespVO {
    @ExcelProperty("Id")
    var id: Long? = null
    @ExcelProperty("Name")
    var name: String? = null
    @ExcelProperty("Code")
    var code: String? = null
}

@ExcelIgnoreUnannotated
class SmsTemplateRespVO : SmsTemplateSimpleRespVO() {
    @ExcelProperty("Type")
    var type: Int? = null
    @ExcelProperty("Status")
    var status: Int? = null
    @ExcelProperty("Content")
    var content: String? = null
    var params: List<String>? = null
    @ExcelProperty("Remark")
    var remark: String? = null
    @ExcelProperty("Provider template id")
    var apiTemplateId: String? = null
    @ExcelProperty("Channel id")
    var channelId: Long? = null
    @ExcelProperty("Channel code")
    var channelCode: String? = null
    @ExcelProperty("Created at")
    var createTime: LocalDateTime? = null
}

class SmsTemplateSendReqVO {
    @field:NotBlank(message = "Mobile number must not be blank")
    var mobile: String? = null

    @field:NotBlank(message = "SMS template code must not be blank")
    var templateCode: String? = null

    var templateParams: Map<String, Any?>? = null
}
