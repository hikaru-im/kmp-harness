package im.hikaru.ruoyi.module.member.controller.app.level.vo.level

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 App - 会员等级 Response VO")
class AppMemberLevelRespVO {
    @field:Schema(description = "等级名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    var name: String? = null
    @field:Schema(description = "等级", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var level: Int? = null
    @field:Schema(description = "升级经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var experience: Int? = null
    @field:Schema(description = "享受折扣", requiredMode = Schema.RequiredMode.REQUIRED, example = "98")
    var discountPercent: Int? = null
    @field:Schema(description = "等级图标", example = "https://www.iocoder.cn/yudao.jpg")
    var icon: String? = null
    @field:Schema(description = "等级背景图", example = "https://www.iocoder.cn/yudao.jpg")
    var backgroundUrl: String? = null
}
