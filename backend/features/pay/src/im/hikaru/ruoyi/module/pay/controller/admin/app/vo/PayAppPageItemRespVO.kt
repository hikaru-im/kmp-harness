package im.hikaru.ruoyi.module.pay.controller.admin.app.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 支付应用信息分页查询 Response VO,相比于支付信息，还会多出应用渠道的开关信息")
class PayAppPageItemRespVO : PayAppBaseVO() {
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
    @field:Schema(description = "已配置的支付渠道编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "[alipay_pc, alipay_wap]")
    var channelCodes: Set<String>? = null
}
