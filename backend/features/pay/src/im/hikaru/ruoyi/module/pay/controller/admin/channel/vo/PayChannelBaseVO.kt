package im.hikaru.ruoyi.module.pay.controller.admin.channel.vo

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

open class PayChannelBaseVO {
    @field:Schema(description = "开启状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "开启状态不能为空")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
    @field:Schema(description = "备注", example = "我是小备注")
    var remark: String? = null
    @field:Schema(description = "渠道费率，单位：百分比", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:NotNull(message = "渠道费率，单位：百分比不能为空")
    var feeRate: Double? = null
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "应用编号不能为空")
    var appId: Long? = null
}
