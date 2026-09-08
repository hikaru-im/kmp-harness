package im.hikaru.ruoyi.module.member.api.config

import im.hikaru.ruoyi.module.member.api.config.dto.MemberConfigRespDTO
import im.hikaru.ruoyi.module.member.convert.config.MemberConfigConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.config.MemberConfigDO
import im.hikaru.ruoyi.module.member.service.config.MemberConfigService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberConfigApiImpl(
    private val memberConfigService: MemberConfigService,
) : MemberConfigApi {
    override fun getConfig(): MemberConfigRespDTO = MemberConfigConvert.convert01(memberConfigService.getConfig() ?: MemberConfigDO())
}
