package im.hikaru.ruoyi.module.mp.controller.admin.account.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

open class MpAccountBaseVO {
    @field:Schema(description = "公众号名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道源码")
    @field:NotEmpty(message = "公众号名称不能为空")
    var name: String? = null
    @field:Schema(description = "公众号微信号", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudaoyuanma")
    @field:NotEmpty(message = "公众号微信号不能为空")
    var account: String? = null
    @field:Schema(description = "公众号 appId", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx5b23ba7a5589ecbb")
    @field:NotEmpty(message = "公众号 appId 不能为空")
    var appId: String? = null
    @field:Schema(description = "公众号 URL", example = "https://example.com")
    var url: String? = null
    @field:Schema(description = "公众号密钥", requiredMode = Schema.RequiredMode.REQUIRED, example = "3a7b3b20c537e52e74afd395eb85f61f")
    @field:NotEmpty(message = "公众号密钥不能为空")
    var appSecret: String? = null
    @field:Schema(description = "公众号 token", requiredMode = Schema.RequiredMode.REQUIRED, example = "kangdayuzhen")
    @field:NotEmpty(message = "公众号 token 不能为空")
    var token: String? = null
    @field:Schema(description = "加密密钥", example = "gjN+Ksei")
    var aesKey: String? = null
    @field:Schema(description = "备注", example = "请关注芋道源码，学习技术")
    var remark: String? = null
}
