package im.hikaru.ruoyi.module.member.controller.app.social.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "用户 APP - 获得获取小程序码 Request VO")
class AppSocialWxaQrcodeReqVO {
    @field:Schema(description = "场景值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    var scene: String? = null
    @field:Schema(description = "页面路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "pages/goods/index")
    @field:NotEmpty(message = "页面路径不能为空")
    var path: String? = null
    @field:Schema(description = "二维码宽度", requiredMode = Schema.RequiredMode.REQUIRED, example = "430")
    var width: Int? = null
    @field:Schema(description = "是/否自动配置线条颜色", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    var autoColor: Boolean? = null
    @field:Schema(description = "是/否检查 page 是否存在", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    var checkPath: Boolean? = null
    @field:Schema(description = "是/否需要透明底色", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    var hyaline: Boolean? = null
}
