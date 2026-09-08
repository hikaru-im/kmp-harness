package im.hikaru.ruoyi.module.pay.convert.channel

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.pay.controller.admin.channel.vo.*
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import kotlinx.datetime.toJavaLocalDateTime

object PayChannelConvert {
    fun convert(req: PayChannelCreateReqVO) = PayChannelDO().apply { code = req.code; status = req.status; remark = req.remark; feeRate = req.feeRate; appId = req.appId }
    fun convert(req: PayChannelUpdateReqVO) = PayChannelDO().apply { id = req.id; status = req.status; remark = req.remark; feeRate = req.feeRate; appId = req.appId }
    fun convert(bean: PayChannelDO, includeConfig: Boolean = true) = PayChannelRespVO().apply { id = bean.id; code = bean.code; status = bean.status; remark = bean.remark; feeRate = bean.feeRate; appId = bean.appId; createTime = bean.createTime?.toJavaLocalDateTime(); config = if (includeConfig) JsonUtils.toJsonString(bean.config) else null }
    fun convertList(list: List<PayChannelDO>) = list.map { convert(it, false) }
    fun convertPage(page: PageResult<PayChannelDO>) = PageResult(page.total, page.list.map { convert(it) })
}
