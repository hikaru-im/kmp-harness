package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.log.SmsLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsLogDO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsTemplateDO
import im.hikaru.ruoyi.module.system.dal.mysql.sms.SmsLogDao
import im.hikaru.ruoyi.module.system.enums.sms.SmsReceiveStatusEnum
import im.hikaru.ruoyi.module.system.enums.sms.SmsSendStatusEnum
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.springframework.stereotype.Service

@Service
class SmsLogServiceImpl : SmsLogService {
    override fun createSmsLog(
        mobile: String,
        userId: Long?,
        userType: Int?,
        isSend: Boolean,
        template: SmsTemplateDO,
        templateContent: String,
        templateParams: Map<String, Any?>,
    ): Long = SmsLogDao.insert(SmsLogDO().apply {
        channelId = template.channelId
        channelCode = template.channelCode
        templateId = template.id
        templateCode = template.code
        templateType = template.type
        this.templateContent = templateContent
        this.templateParams = templateParams
        apiTemplateId = template.apiTemplateId
        this.mobile = mobile
        this.userId = userId
        this.userType = userType
        sendStatus = if (isSend) SmsSendStatusEnum.INIT.status else SmsSendStatusEnum.IGNORE.status
        receiveStatus = SmsReceiveStatusEnum.INIT.status
    })

    override fun updateSmsSendResult(
        id: Long,
        success: Boolean,
        apiSendCode: String?,
        apiSendMsg: String?,
        apiRequestId: String?,
        apiSerialNo: String?,
    ) {
        SmsLogDao.updateById(SmsLogDO().apply {
            this.id = id
            sendStatus = if (success) SmsSendStatusEnum.SUCCESS.status else SmsSendStatusEnum.FAILURE.status
            sendTime = java.time.LocalDateTime.now().toKotlinLocalDateTime()
            this.apiSendCode = apiSendCode
            this.apiSendMsg = apiSendMsg
            this.apiRequestId = apiRequestId
            this.apiSerialNo = apiSerialNo
        })
    }

    override fun updateSmsReceiveResult(
        id: Long?,
        apiSerialNo: String?,
        success: Boolean,
        receiveTime: LocalDateTime?,
        apiReceiveCode: String?,
        apiReceiveMsg: String?,
    ) {
        val resolvedId = id?.takeIf { it != 0L }
            ?: apiSerialNo?.let(SmsLogDao::selectByApiSerialNo)?.id
            ?: return
        SmsLogDao.updateById(SmsLogDO().apply {
            this.id = resolvedId
            receiveStatus = if (success) SmsReceiveStatusEnum.SUCCESS.status else SmsReceiveStatusEnum.FAILURE.status
            this.receiveTime = receiveTime
            this.apiReceiveCode = apiReceiveCode
            this.apiReceiveMsg = apiReceiveMsg
        })
    }

    override fun getSmsLog(id: Long): SmsLogDO? = SmsLogDao.selectById(id)

    override fun getSmsLogPage(req: SmsLogPageReqVO): PageResult<SmsLogDO> = SmsLogDao.selectPage(req)
}
