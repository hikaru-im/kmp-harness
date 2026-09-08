package im.hikaru.ruoyi.module.member.controller.app.auth.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 微信公众号 JSAPI 签名 Response VO")
class AuthWeixinJsapiSignatureRespVO {
    @field:Schema(description = "微信公众号的 appId", requiredMode = Schema.RequiredMode.REQUIRED, example = "hello")
    var appId: String? = null
    @field:Schema(description = "匿名串", requiredMode = Schema.RequiredMode.REQUIRED, example = "world")
    var nonceStr: String? = null
    @field:Schema(description = "时间戳", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var timestamp: Long? = null
    @field:Schema(description = "URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn")
    var url: String? = null
    @field:Schema(description = "签名", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿巴阿巴")
    var signature: String? = null
}
