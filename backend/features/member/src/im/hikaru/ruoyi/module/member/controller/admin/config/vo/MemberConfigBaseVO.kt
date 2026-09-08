package im.hikaru.ruoyi.module.member.controller.admin.config.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

open class MemberConfigBaseVO {
    @field:Schema(description = "积分抵扣开关", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @field:NotNull(message = "积分抵扣开发不能为空")
    var pointTradeDeductEnable: Boolean? = null
    @field:Schema(description = "积分抵扣，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "13506")
    @field:NotNull(message = "积分抵扣不能为空")
    var pointTradeDeductUnitPrice: Int? = null
    @field:Schema(description = "积分抵扣最大值", requiredMode = Schema.RequiredMode.REQUIRED, example = "32428")
    @field:NotNull(message = "积分抵扣最大值不能为空")
    var pointTradeDeductMaxPrice: Int? = null
    @field:Schema(description = "1 元赠送多少分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @field:NotNull(message = "1 元赠送积分不能为空")
    var pointTradeGivePoint: Int? = null
}
