package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.log.SmsLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsLogDO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsTemplateDO
import kotlinx.datetime.LocalDateTime

interface SmsLogService {
    fun createSmsLog(
        mobile: String,
        userId: Long?,
        userType: Int?,
        isSend: Boolean,
        template: SmsTemplateDO,
        templateContent: String,
        templateParams: Map<String, Any?>,
    ): Long

    fun updateSmsSendResult(
        id: Long,
        success: Boolean,
        apiSendCode: String?,
        apiSendMsg: String?,
        apiRequestId: String?,
        apiSerialNo: String?,
    )

    fun updateSmsReceiveResult(
        id: Long?,
        apiSerialNo: String?,
        success: Boolean,
        receiveTime: LocalDateTime?,
        apiReceiveCode: String?,
        apiReceiveMsg: String?,
    )

    fun getSmsLog(id: Long): SmsLogDO?
    fun getSmsLogPage(req: SmsLogPageReqVO): PageResult<SmsLogDO>
}
