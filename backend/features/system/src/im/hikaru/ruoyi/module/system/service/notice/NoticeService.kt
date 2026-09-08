package im.hikaru.ruoyi.module.system.service.notice

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticeSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notice.NoticeDO

interface NoticeService {
    fun createNotice(req: NoticeSaveReqVO): Long

    fun updateNotice(req: NoticeSaveReqVO)

    fun deleteNotice(id: Long)

    fun deleteNoticeList(ids: Collection<Long>)

    fun getNoticePage(req: NoticePageReqVO): PageResult<NoticeDO>

    fun getNotice(id: Long): NoticeDO?
}
