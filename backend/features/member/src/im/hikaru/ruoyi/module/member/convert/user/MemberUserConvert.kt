package im.hikaru.ruoyi.module.member.convert.user

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.api.user.dto.MemberUserRespDTO
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserRespVO
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserInfoRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.group.MemberGroupDO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelDO
import im.hikaru.ruoyi.module.member.dal.dataobject.tag.MemberTagDO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO

object MemberUserConvert {
    fun convert(user: MemberUserDO): AppMemberUserInfoRespVO =
        requireNotNull(BeanUtils.toBean(user, AppMemberUserInfoRespVO::class.java))

    fun convert(user: MemberUserDO, level: MemberLevelDO?): AppMemberUserInfoRespVO = convert(user).apply {
        this.level = level?.let {
            AppMemberUserInfoRespVO.Level().apply {
                id = it.id
                name = it.name
                this.level = it.level
                icon = it.icon
            }
        }
    }

    fun convertDto(user: MemberUserDO): MemberUserRespDTO =
        requireNotNull(BeanUtils.toBean(user, MemberUserRespDTO::class.java))

    fun convertDtoList(users: List<MemberUserDO>): List<MemberUserRespDTO> = users.map(::convertDto)

    fun convertPage(
        page: PageResult<MemberUserDO>,
        tags: List<MemberTagDO>,
        levels: List<MemberLevelDO>,
        groups: List<MemberGroupDO>,
    ): PageResult<MemberUserRespVO> {
        val tagMap = tags.associate { it.id to it.name }
        val levelMap = levels.associate { it.id to it.name }
        val groupMap = groups.associate { it.id to it.name }
        return PageResult(page.total, page.list.map { user ->
            requireNotNull(BeanUtils.toBean(user, MemberUserRespVO::class.java)).apply {
                tagNames = user.tagIds.orEmpty().mapNotNull(tagMap::get)
                levelName = levelMap[user.levelId]
                groupName = groupMap[user.groupId]
            }
        })
    }
}
