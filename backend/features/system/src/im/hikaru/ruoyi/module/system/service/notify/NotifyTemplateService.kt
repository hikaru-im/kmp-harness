package im.hikaru.ruoyi.module.system.service.notify

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO

interface NotifyTemplateService {
    fun createNotifyTemplate(req: NotifyTemplateSaveReqVO): Long
    fun updateNotifyTemplate(req: NotifyTemplateSaveReqVO)
    fun deleteNotifyTemplate(id: Long)
    fun deleteNotifyTemplateList(ids: Collection<Long>)
    fun getNotifyTemplate(id: Long): NotifyTemplateDO?
    fun getNotifyTemplateByCodeFromCache(code: String): NotifyTemplateDO?
    fun getNotifyTemplatePage(req: NotifyTemplatePageReqVO): PageResult<NotifyTemplateDO>
    fun getNotifyTemplateListByStatus(status: Int): List<NotifyTemplateDO>
    fun formatNotifyTemplateContent(content: String, params: Map<String, Any?>): String
}
