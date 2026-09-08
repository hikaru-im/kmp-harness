package im.hikaru.ruoyi.module.pay.dal.dataobject.refund

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import kotlinx.datetime.LocalDateTime

class PayRefundDO : TenantBaseDO {
    var id: Long? = null
    var no: String? = null
    var appId: Long? = null
    var channelId: Long? = null
    var channelCode: String? = null
    var orderId: Long? = null
    var orderNo: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var merchantOrderId: String? = null
    var merchantRefundId: String? = null
    var notifyUrl: String? = null
    var status: Int? = null
    var payPrice: Int? = null
    var refundPrice: Int? = null
    var reason: String? = null
    var userIp: String? = null
    var channelOrderNo: String? = null
    var channelRefundNo: String? = null
    var successTime: LocalDateTime? = null
    var channelErrorCode: String? = null
    var channelErrorMsg: String? = null
    var channelNotifyData: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
