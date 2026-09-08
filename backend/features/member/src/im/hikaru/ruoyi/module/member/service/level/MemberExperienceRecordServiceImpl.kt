package im.hikaru.ruoyi.module.member.service.level

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience.MemberExperienceRecordPageReqVO
import im.hikaru.ruoyi.module.member.convert.level.MemberExperienceRecordConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberExperienceRecordDO
import im.hikaru.ruoyi.module.member.dal.mysql.level.MemberExperienceRecordDao
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberExperienceRecordServiceImpl : MemberExperienceRecordService {
    override fun getExperienceRecord(id: Long): MemberExperienceRecordDO? = MemberExperienceRecordDao.selectById(id)
    override fun getExperienceRecordPage(pageReqVO: MemberExperienceRecordPageReqVO): PageResult<MemberExperienceRecordDO> = MemberExperienceRecordDao.selectPage(pageReqVO)
    override fun getExperienceRecordPage(userId: Long, pageParam: PageParam): PageResult<MemberExperienceRecordDO> = MemberExperienceRecordDao.selectPage(userId, pageParam)
    override fun createExperienceRecord(userId: Long, experience: Int, totalExperience: Int, bizType: MemberExperienceBizTypeEnum, bizId: String) {
        MemberExperienceRecordDao.insert(MemberExperienceRecordConvert.convert(
            userId, experience, totalExperience, bizId, bizType.type, bizType.title,
            bizType.description.replace("{}", experience.toString()),
        ))
    }
}
