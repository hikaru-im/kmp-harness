package im.hikaru.ruoyi.module.member.convert.level

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience.MemberExperienceRecordRespVO
import im.hikaru.ruoyi.module.member.controller.app.level.vo.experience.AppMemberExperienceRecordRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberExperienceRecordDO

object MemberExperienceRecordConvert {
    fun convert(source: MemberExperienceRecordDO): MemberExperienceRecordRespVO = requireNotNull(BeanUtils.toBean(source, MemberExperienceRecordRespVO::class.java))
    fun convertList(source: List<MemberExperienceRecordDO>): List<MemberExperienceRecordRespVO> = BeanUtils.toBean(source, MemberExperienceRecordRespVO::class.java) ?: emptyList()
    fun convertPage(source: PageResult<MemberExperienceRecordDO>): PageResult<MemberExperienceRecordRespVO> = requireNotNull(BeanUtils.toBean(source, MemberExperienceRecordRespVO::class.java))
    fun convert(userId: Long, experience: Int, totalExperience: Int, bizId: String, bizType: Int, title: String, description: String): MemberExperienceRecordDO = MemberExperienceRecordDO().apply {
        this.userId = userId; this.experience = experience; this.totalExperience = totalExperience
        this.bizId = bizId; this.bizType = bizType; this.title = title; this.description = description
    }
    fun convertPage02(source: PageResult<MemberExperienceRecordDO>): PageResult<AppMemberExperienceRecordRespVO> = requireNotNull(BeanUtils.toBean(source, AppMemberExperienceRecordRespVO::class.java))
}
