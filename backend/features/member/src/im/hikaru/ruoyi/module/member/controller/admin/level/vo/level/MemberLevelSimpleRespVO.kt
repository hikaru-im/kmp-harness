package im.hikaru.ruoyi.module.member.controller.admin.level.vo.level

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 会员等级 Response VO")
class MemberLevelSimpleRespVO {
    @field:Schema(description = "编号", example = "6103")
    var id: Long? = null
    @field:Schema(description = "等级名称", example = "芋艿")
    var name: String? = null
    @field:Schema(description = "等级图标", example = "https://www.iocoder.cn/yudao.jpg")
    var icon: String? = null
}
