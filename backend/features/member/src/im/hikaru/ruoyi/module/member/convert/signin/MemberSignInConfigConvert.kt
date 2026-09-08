package im.hikaru.ruoyi.module.member.convert.signin

import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigRespVO
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigUpdateReqVO
import im.hikaru.ruoyi.module.member.controller.app.signin.vo.config.AppMemberSignInConfigRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInConfigDO

object MemberSignInConfigConvert {
    fun convert(source: MemberSignInConfigCreateReqVO): MemberSignInConfigDO = requireNotNull(BeanUtils.toBean(source, MemberSignInConfigDO::class.java))
    fun convert(source: MemberSignInConfigUpdateReqVO): MemberSignInConfigDO = requireNotNull(BeanUtils.toBean(source, MemberSignInConfigDO::class.java))
    fun convert(source: MemberSignInConfigDO): MemberSignInConfigRespVO = requireNotNull(BeanUtils.toBean(source, MemberSignInConfigRespVO::class.java))
    fun convertList(source: List<MemberSignInConfigDO>): List<MemberSignInConfigRespVO> = BeanUtils.toBean(source, MemberSignInConfigRespVO::class.java) ?: emptyList()
    fun convertList02(source: List<MemberSignInConfigDO>): List<AppMemberSignInConfigRespVO> = BeanUtils.toBean(source, AppMemberSignInConfigRespVO::class.java) ?: emptyList()
}
