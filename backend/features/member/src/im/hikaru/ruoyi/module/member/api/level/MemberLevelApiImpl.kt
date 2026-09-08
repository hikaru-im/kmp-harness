package im.hikaru.ruoyi.module.member.api.level

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.api.level.dto.MemberLevelRespDTO
import im.hikaru.ruoyi.module.member.convert.level.MemberLevelConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.EXPERIENCE_BIZ_NOT_SUPPORT
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum
import im.hikaru.ruoyi.module.member.service.level.MemberLevelService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberLevelApiImpl(
    private val memberLevelService: MemberLevelService,
) : MemberLevelApi {
    override fun getMemberLevel(id: Long): MemberLevelRespDTO =
        MemberLevelConvert.convert02(memberLevelService.getLevel(id) ?: throw exception(LEVEL_NOT_EXISTS))

    override fun addExperience(userId: Long, experience: Int, bizType: Int, bizId: String) {
        val type = MemberExperienceBizTypeEnum.getByType(bizType) ?: throw exception(EXPERIENCE_BIZ_NOT_SUPPORT)
        memberLevelService.addExperience(userId, experience, type, bizId)
    }

    override fun reduceExperience(userId: Long, experience: Int, bizType: Int, bizId: String) {
        require(experience > 0)
        addExperience(userId, -experience, bizType, bizId)
    }
}
