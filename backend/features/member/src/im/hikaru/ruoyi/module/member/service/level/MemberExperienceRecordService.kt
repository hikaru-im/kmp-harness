package im.hikaru.ruoyi.module.member.service.level

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience.MemberExperienceRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberExperienceRecordDO
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum

interface MemberExperienceRecordService {
    fun getExperienceRecord(id: Long): MemberExperienceRecordDO?
    fun getExperienceRecordPage(pageReqVO: MemberExperienceRecordPageReqVO): PageResult<MemberExperienceRecordDO>
    fun getExperienceRecordPage(userId: Long, pageParam: PageParam): PageResult<MemberExperienceRecordDO>
    fun createExperienceRecord(userId: Long, experience: Int, totalExperience: Int, bizType: MemberExperienceBizTypeEnum, bizId: String): Unit
}
