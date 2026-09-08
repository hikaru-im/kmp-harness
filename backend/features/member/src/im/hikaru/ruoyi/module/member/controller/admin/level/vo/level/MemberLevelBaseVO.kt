package im.hikaru.ruoyi.module.member.controller.admin.level.vo.level

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.hibernate.validator.constraints.Range
import org.hibernate.validator.constraints.URL

open class MemberLevelBaseVO {
    @field:Schema(description = "等级名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    @field:NotBlank(message = "等级名称不能为空")
    var name: String? = null
    @field:Schema(description = "升级经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @field:NotNull(message = "升级经验不能为空")
    @field:Positive(message = "升级经验必须大于 0")
    var experience: Int? = null
    @field:Schema(description = "等级", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "等级不能为空")
    @field:Positive(message = "等级必须大于 0")
    var level: Int? = null
    @field:Schema(description = "享受折扣", requiredMode = Schema.RequiredMode.REQUIRED, example = "98")
    @field:NotNull(message = "享受折扣不能为空")
    @Range(min = 0, max = 100, message = "享受折扣的范围为 0-100")
    var discountPercent: Int? = null
    @field:Schema(description = "等级图标", example = "https://www.iocoder.cn/yudao.jpg")
    @URL(message = "等级图标必须是 URL 格式")
    var icon: String? = null
    @field:Schema(description = "等级背景图", example = "https://www.iocoder.cn/yudao.jpg")
    @URL(message = "等级背景图必须是 URL 格式")
    var backgroundUrl: String? = null
    @field:Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "状态不能为空")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
}
