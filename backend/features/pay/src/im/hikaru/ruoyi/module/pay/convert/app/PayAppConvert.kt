package im.hikaru.ruoyi.module.pay.convert.app

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.*
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import kotlinx.datetime.toJavaLocalDateTime

object PayAppConvert {
    fun convert(req: PayAppCreateReqVO) = PayAppDO().apply { appKey = req.appKey; name = req.name; status = req.status; remark = req.remark; orderNotifyUrl = req.orderNotifyUrl; refundNotifyUrl = req.refundNotifyUrl; transferNotifyUrl = req.transferNotifyUrl }
    fun convert(req: PayAppUpdateReqVO) = PayAppDO().apply { id = req.id; appKey = req.appKey; name = req.name; status = req.status; remark = req.remark; orderNotifyUrl = req.orderNotifyUrl; refundNotifyUrl = req.refundNotifyUrl; transferNotifyUrl = req.transferNotifyUrl }
    fun convert(bean: PayAppDO) = PayAppRespVO().apply { id = bean.id; appKey = bean.appKey; name = bean.name; status = bean.status; remark = bean.remark; orderNotifyUrl = bean.orderNotifyUrl; refundNotifyUrl = bean.refundNotifyUrl; transferNotifyUrl = bean.transferNotifyUrl; createTime = bean.createTime?.toJavaLocalDateTime() }
    fun convertList(list: List<PayAppDO>) = list.map(::convert)
    fun convertPage(page: PageResult<PayAppDO>, channels: List<PayChannelDO> = emptyList()) = PageResult(page.total, page.list.map { bean -> PayAppPageItemRespVO().apply { id = bean.id; appKey = bean.appKey; name = bean.name; status = bean.status; remark = bean.remark; orderNotifyUrl = bean.orderNotifyUrl; refundNotifyUrl = bean.refundNotifyUrl; transferNotifyUrl = bean.transferNotifyUrl; createTime = bean.createTime?.toJavaLocalDateTime(); channelCodes = channels.filter { it.appId == bean.id }.mapNotNull { it.code }.toSet() } })
}
