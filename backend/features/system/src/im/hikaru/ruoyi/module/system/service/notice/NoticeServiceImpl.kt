package im.hikaru.ruoyi.module.system.service.notice

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticeSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notice.NoticeDO
import im.hikaru.ruoyi.module.system.dal.mysql.notice.NoticeDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.NOTICE_NOT_FOUND
import org.springframework.stereotype.Service

@Service
class NoticeServiceImpl : NoticeService {
    override fun createNotice(req: NoticeSaveReqVO): Long = NoticeDao.insert(req.toEntity())

    override fun updateNotice(req: NoticeSaveReqVO) {
        val id = requireNotNull(req.id)
        validateNoticeExists(id)
        NoticeDao.updateById(req.toEntity())
    }

    override fun deleteNotice(id: Long) {
        validateNoticeExists(id)
        NoticeDao.deleteById(id)
    }

    override fun deleteNoticeList(ids: Collection<Long>) {
        NoticeDao.deleteByIds(ids)
    }

    override fun getNoticePage(req: NoticePageReqVO): PageResult<NoticeDO> = NoticeDao.selectPage(req)

    override fun getNotice(id: Long): NoticeDO? = NoticeDao.selectById(id)

    internal fun validateNoticeExists(id: Long?) {
        if (id != null && NoticeDao.selectById(id) == null) {
            throw ServiceExceptionUtil.exception(NOTICE_NOT_FOUND)
        }
    }

    private fun NoticeSaveReqVO.toEntity() = NoticeDO().apply {
        id = this@toEntity.id
        title = this@toEntity.title
        type = this@toEntity.type
        content = this@toEntity.content
        status = this@toEntity.status
    }
}
