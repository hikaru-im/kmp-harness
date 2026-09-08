package im.hikaru.ruoyi.module.pay.controller.admin.app.vo

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import org.hibernate.validator.constraints.URL

open class PayAppBaseVO {
    @field:Schema(description = "应用标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao")
    @field:NotEmpty(message = "应用标识不能为空")
    var appKey: String? = null
    @field:Schema(description = "应用名", requiredMode = Schema.RequiredMode.REQUIRED, example = "小豆")
    @field:NotNull(message = "应用名不能为空")
    var name: String? = null
    @field:Schema(description = "开启状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @field:NotNull(message = "开启状态不能为空")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
    @field:Schema(description = "备注", example = "我是一个测试应用")
    var remark: String? = null
    @field:Schema(description = "支付结果的回调地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "http://127.0.0.1:48080/pay-callback")
    @field:NotNull(message = "支付结果的回调地址不能为空")
    @URL(message = "支付结果的回调地址必须为 URL 格式")
    var orderNotifyUrl: String? = null
    @field:Schema(description = "退款结果的回调地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "http://127.0.0.1:48080/refund-callback")
    @field:NotNull(message = "退款结果的回调地址不能为空")
    @URL(message = "退款结果的回调地址必须为 URL 格式")
    var refundNotifyUrl: String? = null
    @field:Schema(description = "转账结果的回调地址", example = "http://127.0.0.1:48080/transfer-callback")
    @URL(message = "转账结果的回调地址必须为 URL 格式")
    var transferNotifyUrl: String? = null
}
