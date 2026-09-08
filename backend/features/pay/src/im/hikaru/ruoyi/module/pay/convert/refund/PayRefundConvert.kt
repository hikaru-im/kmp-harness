package im.hikaru.ruoyi.module.pay.convert.refund

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.refund.dto.*
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.*
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
import kotlinx.datetime.toJavaLocalDateTime

object PayRefundConvert {
    fun convert(req: PayRefundCreateReqDTO) = PayRefundDO().apply { userIp = req.userIp; userId = req.userId; userType = req.userType; merchantOrderId = req.merchantOrderId; merchantRefundId = req.merchantRefundId; reason = req.reason; refundPrice = req.price }
    fun convert(bean: PayRefundDO, app: PayAppDO? = null) = PayRefundDetailsRespVO().apply { copyBase(bean); id = bean.id; appName = app?.name; createTime = bean.createTime?.toJavaLocalDateTime(); updateTime = bean.updateTime?.toJavaLocalDateTime() }
    fun convertPage(page: PageResult<PayRefundDO>, apps: Map<Long, PayAppDO>) = PageResult(page.total, page.list.map { bean -> PayRefundPageItemRespVO().apply { copyBase(bean); id = bean.id; appName = apps[bean.appId]?.name; createTime = bean.createTime?.toJavaLocalDateTime() } })
    fun convertExcel(list: List<PayRefundDO>, apps: Map<Long, PayAppDO>) = list.map { bean -> PayRefundExcelVO().apply { id = bean.id; createTime = bean.createTime?.toJavaLocalDateTime(); payPrice = bean.payPrice; refundPrice = bean.refundPrice; merchantRefundId = bean.merchantRefundId; no = bean.no; channelRefundNo = bean.channelRefundNo; merchantOrderId = bean.merchantOrderId; channelOrderNo = bean.channelOrderNo; status = bean.status; channelCode = bean.channelCode; successTime = bean.successTime?.toJavaLocalDateTime(); appName = apps[bean.appId]?.name; reason = bean.reason } }
    fun convertOrder(order: PayOrderDO) = PayRefundDetailsRespVO.Order().apply { subject = order.subject }
    fun convertApi(bean: PayRefundDO) = PayRefundRespDTO().apply { id = bean.id; channelCode = bean.channelCode; status = bean.status; refundPrice = bean.refundPrice; merchantOrderId = bean.merchantOrderId; merchantRefundId = bean.merchantRefundId; successTime = bean.successTime?.toJavaLocalDateTime(); channelErrorCode = bean.channelErrorCode; channelErrorMsg = bean.channelErrorMsg }
    private fun PayRefundBaseVO.copyBase(bean: PayRefundDO) { no = bean.no; appId = bean.appId; channelId = bean.channelId; channelCode = bean.channelCode; orderId = bean.orderId; merchantOrderId = bean.merchantOrderId; merchantRefundId = bean.merchantRefundId; notifyUrl = bean.notifyUrl; status = bean.status; payPrice = bean.payPrice?.toLong(); refundPrice = bean.refundPrice?.toLong(); reason = bean.reason; userIp = bean.userIp; channelOrderNo = bean.channelOrderNo; channelRefundNo = bean.channelRefundNo; successTime = bean.successTime?.toJavaLocalDateTime(); channelErrorCode = bean.channelErrorCode; channelErrorMsg = bean.channelErrorMsg; channelNotifyData = bean.channelNotifyData }
}
