package im.hikaru.ruoyi.module.member.api.level

import im.hikaru.ruoyi.module.member.api.level.dto.MemberLevelRespDTO
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum

interface MemberLevelApi {
    fun getMemberLevel(id: Long): MemberLevelRespDTO
    fun addExperience(userId: Long, experience: Int, bizType: Int, bizId: String): Unit
    fun reduceExperience(userId: Long, experience: Int, bizType: Int, bizId: String): Unit
}
