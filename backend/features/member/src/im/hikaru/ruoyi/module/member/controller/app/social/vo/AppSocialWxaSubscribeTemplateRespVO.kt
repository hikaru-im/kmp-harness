package im.hikaru.ruoyi.module.member.controller.app.social.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 获得小程序订阅模版 Response VO")
class AppSocialWxaSubscribeTemplateRespVO {
    @field:Schema(description = "模版编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "9Aw5ZV1j9xdWTFEkqCpZ7mIBbSC34khK55OtzUPl0rU")
    var id: String? = null
    @field:Schema(description = "模版标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "订单支付通知")
    var title: String? = null
    @field:Schema(description = "模版内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "{ {result.DATA} }\\n\\n领奖金额:{ {withdrawMoney.DATA} }\\n领奖时间:    { {withdrawTime.DATA} }")
    var content: String? = null
    @field:Schema(description = "模板内容示例", requiredMode = Schema.RequiredMode.REQUIRED, example = "下单时间:2016年8月8日")
    var example: String? = null
    @field:Schema(description = "模版类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    var type: Int? = null
}
