package im.hikaru.ruoyi.module.mp.service.message

import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateListReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateSendReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageTemplateDO

interface MpMessageTemplateService {
    fun deleteMessageTemplate(id: Long): Unit
    fun getMessageTemplate(id: Long): MpMessageTemplateDO
    fun getMessageTemplateList(listReqVO: MpMessageTemplateListReqVO): List<MpMessageTemplateDO>
    fun syncMessageTemplate(accountId: Long): Unit
    fun sendMessageTempalte(sendReqVO: MpMessageTemplateSendReqVO): Unit
}
