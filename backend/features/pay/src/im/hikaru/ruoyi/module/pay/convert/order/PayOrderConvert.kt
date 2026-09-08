package im.hikaru.ruoyi.module.pay.convert.order

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.*
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.*
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO as ClientPayOrderRespDTO
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

object PayOrderConvert {
    fun convert(req: PayOrderCreateReqDTO) = PayOrderDO().apply { userIp = req.userIp; userId = req.userId; userType = req.userType; merchantOrderId = req.merchantOrderId; subject = req.subject; body = req.body; price = req.price; expireTime = req.expireTime?.toKotlinLocalDateTime() }
    fun convert(req: PayOrderSubmitReqVO, userIp: String) = PayOrderExtensionDO().apply { channelExtras = req.channelExtras; this.userIp = userIp }
    fun unified(req: PayOrderSubmitReqVO, userIp: String) = PayOrderUnifiedReqDTO().apply { this.userIp = userIp; channelExtras = req.channelExtras; displayMode = req.displayMode; returnUrl = req.returnUrl }
    fun convert(bean: PayOrderDO) = PayOrderRespVO().apply { copyBase(bean); createTime = bean.createTime?.toJavaLocalDateTime() }
    fun convertPage(page: PageResult<PayOrderDO>, apps: Map<Long, PayAppDO>) = PageResult(page.total, page.list.map { bean -> PayOrderPageItemRespVO().apply { copyBase(bean); appName = apps[bean.appId]?.name; createTime = bean.createTime?.toJavaLocalDateTime() } })
    fun convertDetail(order: PayOrderDO, extension: PayOrderExtensionDO?, app: PayAppDO?) = PayOrderDetailsRespVO().apply { copyBase(order); id = order.id; appName = app?.name; createTime = order.createTime?.toJavaLocalDateTime(); updateTime = order.updateTime?.toJavaLocalDateTime(); this.extension = extension?.let { PayOrderDetailsRespVO.PayOrderExtension().apply { no = it.no; channelNotifyData = it.channelNotifyData } } }
    fun convertExcel(list: List<PayOrderDO>, apps: Map<Long, PayAppDO>) = list.map { bean -> PayOrderExcelVO().apply { id = bean.id; createTime = bean.createTime?.toJavaLocalDateTime(); price = bean.price; refundPrice = bean.refundPrice; channelFeePrice = bean.channelFeePrice; merchantOrderId = bean.merchantOrderId; no = bean.no; channelOrderNo = bean.channelOrderNo; status = bean.status; channelCode = bean.channelCode; successTime = bean.successTime?.toJavaLocalDateTime(); expireTime = bean.expireTime?.toJavaLocalDateTime(); appName = apps[bean.appId]?.name; subject = bean.subject; body = bean.body } }
    fun convert(req: PayOrderDO, resp: ClientPayOrderRespDTO) = PayOrderSubmitRespVO().apply { status = resp.status ?: req.status; displayMode = resp.displayMode; displayContent = resp.displayContent }
    fun convertApi(bean: PayOrderDO) = PayOrderRespDTO().apply { id = bean.id; channelCode = bean.channelCode; merchantOrderId = bean.merchantOrderId; subject = bean.subject; price = bean.price; status = bean.status; successTime = bean.successTime?.toJavaLocalDateTime(); channelUserId = bean.channelUserId; channelOrderNo = bean.channelOrderNo }
    private fun PayOrderBaseVO.copyBase(bean: PayOrderDO) { appId = bean.appId; channelId = bean.channelId; channelCode = bean.channelCode; merchantOrderId = bean.merchantOrderId; subject = bean.subject; body = bean.body; notifyUrl = bean.notifyUrl; price = bean.price?.toLong(); channelFeeRate = bean.channelFeeRate; channelFeePrice = bean.channelFeePrice; status = bean.status; userIp = bean.userIp; expireTime = bean.expireTime?.toJavaLocalDateTime(); successTime = bean.successTime?.toJavaLocalDateTime(); extensionId = bean.extensionId; no = bean.no; refundPrice = bean.refundPrice?.toLong(); channelUserId = bean.channelUserId; channelOrderNo = bean.channelOrderNo }
}
