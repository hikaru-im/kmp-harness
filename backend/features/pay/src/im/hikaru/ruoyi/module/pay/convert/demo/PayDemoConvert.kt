package im.hikaru.ruoyi.module.pay.convert.demo

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.order.*
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.withdraw.*
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.*
import kotlinx.datetime.toJavaLocalDateTime

object PayDemoConvert {
    fun orderCreate(req: PayDemoOrderCreateReqVO, userId: Long) = PayDemoOrderDO().apply { this.userId = userId; spuId = req.spuId; spuName = "SPU-${req.spuId}"; price = 1; payStatus = false; refundPrice = 0 }
    fun order(bean: PayDemoOrderDO) = PayDemoOrderRespVO().apply { id = bean.id; userId = bean.userId; spuId = bean.spuId; spuName = bean.spuName; price = bean.price; payStatus = bean.payStatus; payOrderId = bean.payOrderId; payTime = bean.payTime?.toJavaLocalDateTime(); payChannelCode = bean.payChannelCode; payRefundId = bean.payRefundId; refundPrice = bean.refundPrice; refundTime = bean.refundTime?.toJavaLocalDateTime(); transferChannelPackageInfo = bean.transferChannelPackageInfo; createTime = bean.createTime?.toJavaLocalDateTime() }
    fun orderPage(page: PageResult<PayDemoOrderDO>) = PageResult(page.total, page.list.map(::order))
    fun withdrawCreate(req: PayDemoWithdrawCreateReqVO) = PayDemoWithdrawDO().apply { subject = req.subject; price = req.price; userAccount = req.userAccount; userName = req.userName; type = req.type; status = 0 }
    fun withdraw(bean: PayDemoWithdrawDO) = PayDemoWithdrawRespVO().apply { id = bean.id; subject = bean.subject; price = bean.price; userName = bean.userName; userAccount = bean.userAccount; type = bean.type; status = bean.status; payTransferId = bean.payTransferId; transferChannelCode = bean.transferChannelCode; transferTime = bean.transferTime?.toJavaLocalDateTime(); transferErrorMsg = bean.transferErrorMsg }
    fun withdrawPage(page: PageResult<PayDemoWithdrawDO>) = PageResult(page.total, page.list.map(::withdraw))
}
