package im.hikaru.ruoyi.module.member.service.group

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.group.MemberGroupDO
import jakarta.validation.Valid

interface MemberGroupService {
    fun createGroup(@Valid createReqVO: MemberGroupCreateReqVO): Long
    fun updateGroup(@Valid updateReqVO: MemberGroupUpdateReqVO): Unit
    fun deleteGroup(id: Long): Unit
    fun getGroup(id: Long): MemberGroupDO?
    fun getGroupList(ids: Collection<Long>): List<MemberGroupDO>
    fun getGroupPage(pageReqVO: MemberGroupPageReqVO): PageResult<MemberGroupDO>
    fun getGroupListByStatus(status: Int): List<MemberGroupDO>
    fun getEnableGroupList(): List<MemberGroupDO> = getGroupListByStatus(0)
}
