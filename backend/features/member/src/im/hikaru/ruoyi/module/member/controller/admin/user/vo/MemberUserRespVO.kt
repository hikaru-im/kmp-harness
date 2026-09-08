package im.hikaru.ruoyi.module.member.controller.admin.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 会员用户 Response VO")
class MemberUserRespVO : MemberUserBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "23788")
    var id: Long? = null
    @field:Schema(description = "注册 IP", requiredMode = Schema.RequiredMode.REQUIRED, example = "127.0.0.1")
    var registerIp: String? = null
    @field:Schema(description = "最后登录IP", requiredMode = Schema.RequiredMode.REQUIRED, example = "127.0.0.1")
    var loginIp: String? = null
    @field:Schema(description = "最后登录时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var loginDate: LocalDateTime? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
    @field:Schema(description = "积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var point: Int? = null
    @field:Schema(description = "总积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    var totalPoint: Int? = null
    @field:Schema(description = "会员标签", example = "[红色, 快乐]")
    var tagNames: List<String>? = null
    @field:Schema(description = "会员等级", example = "黄金会员")
    var levelName: String? = null
    @field:Schema(description = "用户分组", example = "购物达人")
    var groupName: String? = null
    @field:Schema(description = "用户经验值", requiredMode = Schema.RequiredMode.REQUIRED, example = "200")
    var experience: Int? = null
}
