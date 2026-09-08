package im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 发起转账 Response VO")
class PayTransferCreateRespVO {
    @field:Schema(description = "转账单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var id: Long? = null
    @field:Schema(description = "转账状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var status: Int? = null
}
