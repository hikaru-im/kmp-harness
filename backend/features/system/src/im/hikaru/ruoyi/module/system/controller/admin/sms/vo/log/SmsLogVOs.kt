package im.hikaru.ruoyi.module.system.controller.admin.sms.vo.log

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime

class SmsLogPageReqVO : PageParam() {
    var channelId: Long? = null
    var templateId: Long? = null
    var mobile: String? = null
    var sendStatus: Int? = null
    var sendTime: List<KotlinLocalDateTime>? = null
    var receiveStatus: Int? = null
    var receiveTime: List<KotlinLocalDateTime>? = null
}

@ExcelIgnoreUnannotated
class SmsLogRespVO {
    @ExcelProperty("Id")
    var id: Long? = null
    @ExcelProperty("Channel id")
    var channelId: Long? = null
    @ExcelProperty("Channel code")
    var channelCode: String? = null
    @ExcelProperty("Template id")
    var templateId: Long? = null
    @ExcelProperty("Template code")
    var templateCode: String? = null
    @ExcelProperty("Template type")
    var templateType: Int? = null
    @ExcelProperty("Content")
    var templateContent: String? = null
    var templateParams: Map<String, Any?>? = null
    @ExcelProperty("Provider template id")
    var apiTemplateId: String? = null
    @ExcelProperty("Mobile")
    var mobile: String? = null
    @ExcelProperty("User id")
    var userId: Long? = null
    @ExcelProperty("User type")
    var userType: Int? = null
    @ExcelProperty("Send status")
    var sendStatus: Int? = null
    @ExcelProperty("Sent at")
    var sendTime: LocalDateTime? = null
    @ExcelProperty("Provider send code")
    var apiSendCode: String? = null
    @ExcelProperty("Provider send message")
    var apiSendMsg: String? = null
    @ExcelProperty("Provider request id")
    var apiRequestId: String? = null
    @ExcelProperty("Provider serial number")
    var apiSerialNo: String? = null
    @ExcelProperty("Receive status")
    var receiveStatus: Int? = null
    @ExcelProperty("Received at")
    var receiveTime: LocalDateTime? = null
    @ExcelProperty("Provider receive code")
    var apiReceiveCode: String? = null
    @ExcelProperty("Provider receive message")
    var apiReceiveMsg: String? = null
    @ExcelProperty("Created at")
    var createTime: LocalDateTime? = null
}
