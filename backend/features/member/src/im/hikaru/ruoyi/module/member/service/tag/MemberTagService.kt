package im.hikaru.ruoyi.module.member.service.tag

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.tag.MemberTagDO
import jakarta.validation.Valid

interface MemberTagService {
    fun createTag(@Valid createReqVO: MemberTagCreateReqVO): Long
    fun updateTag(@Valid updateReqVO: MemberTagUpdateReqVO): Unit
    fun deleteTag(id: Long): Unit
    fun getTag(id: Long): MemberTagDO?
    fun getTagList(ids: Collection<Long>): List<MemberTagDO>
    fun getTagPage(pageReqVO: MemberTagPageReqVO): PageResult<MemberTagDO>
    fun getTagList(): List<MemberTagDO>
}
