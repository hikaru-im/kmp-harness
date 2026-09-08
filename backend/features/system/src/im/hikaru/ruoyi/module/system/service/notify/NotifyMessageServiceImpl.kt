package im.hikaru.ruoyi.module.system.service.notify

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyMessageDO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO
import im.hikaru.ruoyi.module.system.dal.mysql.notify.NotifyMessageDao
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class NotifyMessageServiceImpl : NotifyMessageService {
    override fun createNotifyMessage(
        userId: Long, userType: Int, template: NotifyTemplateDO,
        templateContent: String, templateParams: Map<String, Any?>,
    ): Long = NotifyMessageDao.insert(NotifyMessageDO().apply {
        this.userId = userId
        this.userType = userType
        templateId = template.id
        templateCode = template.code
        templateType = template.type
        templateNickname = template.nickname
        this.templateContent = templateContent
        this.templateParams = templateParams
        readStatus = false
    })

    override fun getNotifyMessagePage(req: NotifyMessagePageReqVO): PageResult<NotifyMessageDO> = NotifyMessageDao.selectPage(req)

    override fun getMyNotifyMessagePage(
        req: NotifyMessageMyPageReqVO,
        userId: Long,
        userType: Int,
    ): PageResult<NotifyMessageDO> = NotifyMessageDao.selectMyPage(req, userId, userType)

    override fun getNotifyMessage(id: Long): NotifyMessageDO? = NotifyMessageDao.selectById(id)

    override fun getUnreadNotifyMessageList(userId: Long, userType: Int, size: Int): List<NotifyMessageDO> =
        NotifyMessageDao.selectUnreadList(userId, userType, size)

    override fun getUnreadNotifyMessageCount(userId: Long, userType: Int): Long =
        NotifyMessageDao.selectUnreadCount(userId, userType)

    override fun updateNotifyMessageRead(ids: Collection<Long>, userId: Long, userType: Int): Int =
        NotifyMessageDao.updateRead(ids, userId, userType)

    override fun updateAllNotifyMessageRead(userId: Long, userType: Int): Int =
        NotifyMessageDao.updateAllRead(userId, userType)
}
