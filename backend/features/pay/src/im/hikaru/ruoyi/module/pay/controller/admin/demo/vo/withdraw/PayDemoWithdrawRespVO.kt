package im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.withdraw

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 示例转账单创建 Request VO")
class PayDemoWithdrawRespVO {
    @field:Schema(description = "转账单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "提现标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "吃饭报销")
    var subject: String? = null
    @field:Schema(description = "提现金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "22338")
    var price: Int? = null
    @field:Schema(description = "收款人姓名", example = "test")
    var userName: String? = null
    @field:Schema(description = "收款人账号", example = "32167")
    var userAccount: String? = null
    @field:Schema(description = "提现类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var type: Int? = null
    @field:Schema(description = "提现状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    var status: Int? = null
    @field:Schema(description = "转账单编号", example = "23695")
    var payTransferId: Long? = null
    @field:Schema(description = "转账渠道", example = "wx_lite")
    var transferChannelCode: String? = null
    @field:Schema(description = "转账成功时间")
    var transferTime: LocalDateTime? = null
    @field:Schema(description = "转账失败原因", example = "IP 不正确")
    var transferErrorMsg: String? = null
}
