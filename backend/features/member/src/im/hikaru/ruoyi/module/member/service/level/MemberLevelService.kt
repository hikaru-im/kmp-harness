package im.hikaru.ruoyi.module.member.service.level

import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelListReqVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelUpdateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserUpdateLevelReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelDO
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum
import jakarta.validation.Valid

interface MemberLevelService {
    fun createLevel(@Valid createReqVO: MemberLevelCreateReqVO): Long
    fun updateLevel(@Valid updateReqVO: MemberLevelUpdateReqVO): Unit
    fun deleteLevel(id: Long): Unit
    fun getLevel(id: Long): MemberLevelDO?
    fun getLevelList(ids: Collection<Long>): List<MemberLevelDO>
    fun getLevelList(listReqVO: MemberLevelListReqVO): List<MemberLevelDO>
    fun getLevelListByStatus(status: Int): List<MemberLevelDO>
    fun getEnableLevelList(): List<MemberLevelDO> = getLevelListByStatus(im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum.ENABLE.status)
    fun updateUserLevel(updateReqVO: MemberUserUpdateLevelReqVO): Unit
    fun addExperience(userId: Long, experience: Int, bizType: MemberExperienceBizTypeEnum, bizId: String): Unit
}
