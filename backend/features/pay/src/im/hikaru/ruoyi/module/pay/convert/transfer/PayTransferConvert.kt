package im.hikaru.ruoyi.module.pay.convert.transfer

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateRespDTO
import im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo.PayTransferRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.transfer.PayTransferDO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import kotlinx.datetime.toJavaLocalDateTime

object PayTransferConvert {
    fun convert(bean: PayTransferDO, appName: String? = null) = PayTransferRespVO().apply { id = bean.id; no = bean.no; appId = bean.appId; this.appName = appName; channelId = bean.channelId; channelCode = bean.channelCode; merchantTransferId = bean.merchantTransferId; subject = bean.subject; price = bean.price; userAccount = bean.userAccount; userName = bean.userName; status = bean.status; successTime = bean.successTime?.toJavaLocalDateTime(); notifyUrl = bean.notifyUrl; userIp = bean.userIp; channelExtras = bean.channelExtras; channelTransferNo = bean.channelTransferNo; channelErrorCode = bean.channelErrorCode; channelErrorMsg = bean.channelErrorMsg; channelNotifyData = bean.channelNotifyData; createTime = bean.createTime?.toJavaLocalDateTime() }
    fun convertPage(page: PageResult<PayTransferDO>) = PageResult(page.total, page.list.map { convert(it) })
    fun createResponse(bean: PayTransferDO) = PayTransferCreateRespDTO().apply { id = bean.id; channelPackageInfo = bean.channelPackageInfo }
    fun api(bean: PayTransferDO) = im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferRespDTO().apply { id = bean.id; no = bean.no; channelCode = bean.channelCode; merchantTransferId = bean.merchantTransferId; price = bean.price; status = bean.status; successTime = bean.successTime?.toJavaLocalDateTime(); channelErrorCode = bean.channelErrorCode; channelErrorMsg = bean.channelErrorMsg; channelPackageInfo = bean.channelPackageInfo }
    fun fromNotify(bean: PayTransferRespDTO) = bean
}
