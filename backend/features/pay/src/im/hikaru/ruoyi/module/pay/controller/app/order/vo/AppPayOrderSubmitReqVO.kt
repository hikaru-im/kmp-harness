package im.hikaru.ruoyi.module.pay.controller.app.order.vo

import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "用户 APP - 支付订单提交 Request VO")
class AppPayOrderSubmitReqVO : PayOrderSubmitReqVO() {
}
