package im.hikaru.ruoyi.module.member.convert.config

import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.api.config.dto.MemberConfigRespDTO
import im.hikaru.ruoyi.module.member.controller.admin.config.vo.MemberConfigRespVO
import im.hikaru.ruoyi.module.member.controller.admin.config.vo.MemberConfigSaveReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.config.MemberConfigDO

object MemberConfigConvert {
    fun convert(source: MemberConfigDO): MemberConfigRespVO = requireNotNull(BeanUtils.toBean(source, MemberConfigRespVO::class.java))
    fun convert(source: MemberConfigSaveReqVO): MemberConfigDO = requireNotNull(BeanUtils.toBean(source, MemberConfigDO::class.java))
    fun convert01(source: MemberConfigDO): MemberConfigRespDTO = requireNotNull(BeanUtils.toBean(source, MemberConfigRespDTO::class.java))
}
