package im.hikaru.ruoyi.module.member.controller.admin.group.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 用户分组 Response VO")
class MemberGroupSimpleRespVO {
    @field:Schema(description = "编号", example = "6103")
    var id: Long? = null
    @field:Schema(description = "等级名称", example = "芋艿")
    var name: String? = null
}
