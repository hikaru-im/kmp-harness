package im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero

open class MemberSignInConfigBaseVO {
    @field:Schema(description = "签到第 x 天", requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
    @field:NotNull(message = "签到天数不能为空")
    var day: Int? = null
    @field:Schema(description = "奖励积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:NotNull(message = "奖励积分不能为空")
    @field:PositiveOrZero(message = "奖励积分不能小于 0")
    var point: Int? = null
    @field:Schema(description = "奖励经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:NotNull(message = "奖励经验不能为空")
    @field:PositiveOrZero(message = "奖励经验不能小于 0")
    var experience: Int? = null
    @field:Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "状态不能为空")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null

    @get:AssertTrue(message = "签到奖励积分和经验不能同时为空")
    @get:JsonIgnore
    val isConfigAward: Boolean
        get() = point != 0 || experience != 0
}
