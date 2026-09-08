package im.hikaru.ruoyi.module.member.convert.tag

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagRespVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.tag.MemberTagDO

object MemberTagConvert {
    fun convert(source: MemberTagCreateReqVO): MemberTagDO = requireNotNull(BeanUtils.toBean(source, MemberTagDO::class.java))
    fun convert(source: MemberTagUpdateReqVO): MemberTagDO = requireNotNull(BeanUtils.toBean(source, MemberTagDO::class.java))
    fun convert(source: MemberTagDO): MemberTagRespVO = requireNotNull(BeanUtils.toBean(source, MemberTagRespVO::class.java))
    fun convertList(source: List<MemberTagDO>): List<MemberTagRespVO> = BeanUtils.toBean(source, MemberTagRespVO::class.java) ?: emptyList()
    fun convertPage(source: PageResult<MemberTagDO>): PageResult<MemberTagRespVO> = requireNotNull(BeanUtils.toBean(source, MemberTagRespVO::class.java))
}
