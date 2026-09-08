package im.hikaru.ruoyi.module.member.controller.admin.level.vo.level

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 会员等级列表筛选 Request VO")
class MemberLevelListReqVO {
    @field:Schema(description = "等级名称", example = "芋艿")
    var name: String? = null
    @field:Schema(description = "状态", example = "1")
    var status: Int? = null
}
