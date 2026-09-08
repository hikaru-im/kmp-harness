package im.hikaru.ruoyi.module.member.api.config

import im.hikaru.ruoyi.module.member.api.config.dto.MemberConfigRespDTO

interface MemberConfigApi {
    fun getConfig(): MemberConfigRespDTO
}
