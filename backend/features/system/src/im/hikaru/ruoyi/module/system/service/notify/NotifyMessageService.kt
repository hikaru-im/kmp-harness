package im.hikaru.ruoyi.module.system.service.notify

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyMessageDO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO

interface NotifyMessageService {
    fun createNotifyMessage(
        userId: Long, userType: Int, template: NotifyTemplateDO,
        templateContent: String, templateParams: Map<String, Any?>,
    ): Long
    fun getNotifyMessagePage(req: NotifyMessagePageReqVO): PageResult<NotifyMessageDO>
    fun getMyNotifyMessagePage(req: NotifyMessageMyPageReqVO, userId: Long, userType: Int): PageResult<NotifyMessageDO>
    fun getNotifyMessage(id: Long): NotifyMessageDO?
    fun getUnreadNotifyMessageList(userId: Long, userType: Int, size: Int): List<NotifyMessageDO>
    fun getUnreadNotifyMessageCount(userId: Long, userType: Int): Long
    fun updateNotifyMessageRead(ids: Collection<Long>, userId: Long, userType: Int): Int
    fun updateAllNotifyMessageRead(userId: Long, userType: Int): Int
}
