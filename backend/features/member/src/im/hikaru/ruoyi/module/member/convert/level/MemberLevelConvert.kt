package im.hikaru.ruoyi.module.member.convert.level

import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.api.level.dto.MemberLevelRespDTO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelRespVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelSimpleRespVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelUpdateReqVO
import im.hikaru.ruoyi.module.member.controller.app.level.vo.level.AppMemberLevelRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelDO

object MemberLevelConvert {
    fun convert(source: MemberLevelCreateReqVO): MemberLevelDO = requireNotNull(BeanUtils.toBean(source, MemberLevelDO::class.java))
    fun convert(source: MemberLevelUpdateReqVO): MemberLevelDO = requireNotNull(BeanUtils.toBean(source, MemberLevelDO::class.java))
    fun convert(source: MemberLevelDO): MemberLevelRespVO = requireNotNull(BeanUtils.toBean(source, MemberLevelRespVO::class.java))
    fun convertList(source: List<MemberLevelDO>): List<MemberLevelRespVO> = BeanUtils.toBean(source, MemberLevelRespVO::class.java) ?: emptyList()
    fun convertSimpleList(source: List<MemberLevelDO>): List<MemberLevelSimpleRespVO> = BeanUtils.toBean(source, MemberLevelSimpleRespVO::class.java) ?: emptyList()
    fun convertList02(source: List<MemberLevelDO>): List<AppMemberLevelRespVO> = BeanUtils.toBean(source, AppMemberLevelRespVO::class.java) ?: emptyList()
    fun convert02(source: MemberLevelDO): MemberLevelRespDTO = requireNotNull(BeanUtils.toBean(source, MemberLevelRespDTO::class.java))
}
