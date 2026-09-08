package im.hikaru.ruoyi.module.member.service.config

import im.hikaru.ruoyi.module.member.controller.admin.config.vo.MemberConfigSaveReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.config.MemberConfigDO
import jakarta.validation.Valid

interface MemberConfigService {
    fun saveConfig(@Valid saveReqVO: MemberConfigSaveReqVO): Unit
    fun getConfig(): MemberConfigDO?
}
