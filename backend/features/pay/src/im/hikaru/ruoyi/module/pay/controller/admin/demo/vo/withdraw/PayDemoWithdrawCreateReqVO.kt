package im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.withdraw

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.pay.enums.demo.PayDemoWithdrawTypeEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

@Schema(description = "管理后台 - 示例提现单创建 Request VO")
class PayDemoWithdrawCreateReqVO {
    @field:Schema(description = "提现标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿是一种菜")
    @field:NotEmpty(message = "提现标题不能为空")
    var subject: String? = null
    @field:Schema(description = "提现金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @field:NotNull(message = "提现金额不能为空")
    @field:Min(value = 1, message = "提现金额必须大于零")
    var price: Int? = null
    @field:Schema(description = "收款人账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "test1")
    @field:NotBlank(message = "收款人账号不能为空")
    var userAccount: String? = null
    @field:Schema(description = "收款人姓名", example = "test1")
    var userName: String? = null
    @field:Schema(description = "提现方式", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "提现方式不能为空")
    @field:InEnum(PayDemoWithdrawTypeEnum::class)
    var type: Int? = null
}
