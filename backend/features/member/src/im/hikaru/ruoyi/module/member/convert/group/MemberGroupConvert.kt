package im.hikaru.ruoyi.module.member.convert.group

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupRespVO
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupSimpleRespVO
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.group.MemberGroupDO

object MemberGroupConvert {
    fun convert(source: MemberGroupCreateReqVO): MemberGroupDO = requireNotNull(BeanUtils.toBean(source, MemberGroupDO::class.java))
    fun convert(source: MemberGroupUpdateReqVO): MemberGroupDO = requireNotNull(BeanUtils.toBean(source, MemberGroupDO::class.java))
    fun convert(source: MemberGroupDO): MemberGroupRespVO = requireNotNull(BeanUtils.toBean(source, MemberGroupRespVO::class.java))
    fun convertList(source: List<MemberGroupDO>): List<MemberGroupRespVO> = BeanUtils.toBean(source, MemberGroupRespVO::class.java) ?: emptyList()
    fun convertPage(source: PageResult<MemberGroupDO>): PageResult<MemberGroupRespVO> = requireNotNull(BeanUtils.toBean(source, MemberGroupRespVO::class.java))
    fun convertSimpleList(source: List<MemberGroupDO>): List<MemberGroupSimpleRespVO> = BeanUtils.toBean(source, MemberGroupSimpleRespVO::class.java) ?: emptyList()
}
