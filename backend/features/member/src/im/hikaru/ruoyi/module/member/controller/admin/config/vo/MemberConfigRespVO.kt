package im.hikaru.ruoyi.module.member.controller.admin.config.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 会员配置 Response VO")
class MemberConfigRespVO : MemberConfigBaseVO() {
    @field:Schema(description = "自增主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
}
