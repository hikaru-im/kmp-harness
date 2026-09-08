package im.hikaru.ruoyi.module.member.controller.app.user.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 用户个人信息 Response VO")
class AppMemberUserInfoRespVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var id: Long? = null
    @field:Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    var nickname: String? = null
    @field:Schema(description = "用户头像", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/xxx.png")
    var avatar: String? = null
    @field:Schema(description = "个人资料版本", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var profileVersion: Long? = null
    @field:Schema(description = "用户手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
    var mobile: String? = null
    @field:Schema(description = "邮箱", example = "member@iocoder.cn")
    var email: String? = null
    @field:Schema(description = "用户性别", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var sex: Int? = null
    @field:Schema(description = "积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var point: Int? = null
    @field:Schema(description = "经验值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var experience: Int? = null
    @field:Schema(description = "用户等级")
    var level: Level? = null
    @field:Schema(description = "是否成为推广员", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    var brokerageEnabled: Boolean? = null

    @Schema(description = "用户 App - 会员等级")
    class Level {
        @field:Schema(description = "等级编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        var id: Long? = null
        @field:Schema(description = "等级名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
        var name: String? = null
        @field:Schema(description = "等级", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        var level: Int? = null
        @field:Schema(description = "等级图标", example = "https://www.iocoder.cn/yudao.jpg")
        var icon: String? = null
    }
}
