package im.hikaru.ruoyi.module.mp.service.tag

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagUpdateReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.tag.MpTagDO
import jakarta.validation.Valid

interface MpTagService {
    fun createTag(@Valid createReqVO: MpTagCreateReqVO): Long
    fun updateTag(@Valid updateReqVO: MpTagUpdateReqVO): Unit
    fun deleteTag(id: Long): Unit
    fun getTagPage(pageReqVO: MpTagPageReqVO): PageResult<MpTagDO>
    fun get(id: Long): MpTagDO
    fun getTagList(): List<MpTagDO>
    fun syncTag(accountId: Long): Unit
}
