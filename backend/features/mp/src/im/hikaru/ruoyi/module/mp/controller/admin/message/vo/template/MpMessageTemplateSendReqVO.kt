package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号消息模版发送 Request VO")
class MpMessageTemplateSendReqVO {
    @field:Schema(description = "模版主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "7019")
    @field:NotNull(message = "模版主键不能为空")
    var id: Long? = null
    @field:Schema(description = "公众号粉丝的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "公众号粉丝的编号不能为空")
    var userId: Long? = null
    @field:Schema(description = "模板跳转链接")
    var url: String? = null
    @field:Schema(description = "跳转小程序时填写")
    var miniprogram: String? = null
    @field:Schema(description = "模板内容")
    var data: Map<String, String>? = null
}
