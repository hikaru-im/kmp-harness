package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 钱包流水分页 Request VO")
class PayWalletTransactionPageReqVO : PageParam() {
    @field:Schema(description = "钱包编号", example = "888")
    var walletId: Long? = null
    @field:Schema(description = "用户编号", example = "1024")
    var userId: Long? = null
    @field:Schema(description = "用户类型", example = "1")
    @field:InEnum(UserTypeEnum::class)
    var userType: Int? = null
}
