package im.hikaru.ruoyi.module.member.service.level

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.record.MemberLevelRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelRecordDO
import im.hikaru.ruoyi.module.member.dal.mysql.level.MemberLevelRecordDao
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberLevelRecordServiceImpl : MemberLevelRecordService {
    override fun getLevelRecord(id: Long): MemberLevelRecordDO? = MemberLevelRecordDao.selectById(id)
    override fun getLevelRecordPage(pageReqVO: MemberLevelRecordPageReqVO): PageResult<MemberLevelRecordDO> = MemberLevelRecordDao.selectPage(pageReqVO)
    override fun createLevelRecord(levelRecord: MemberLevelRecordDO) { MemberLevelRecordDao.insert(levelRecord) }
}
