package im.hikaru.ruoyi.module.member.service.level

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.record.MemberLevelRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelRecordDO

interface MemberLevelRecordService {
    fun getLevelRecord(id: Long): MemberLevelRecordDO?
    fun getLevelRecordPage(pageReqVO: MemberLevelRecordPageReqVO): PageResult<MemberLevelRecordDO>
    fun createLevelRecord(levelRecord: MemberLevelRecordDO): Unit
}
