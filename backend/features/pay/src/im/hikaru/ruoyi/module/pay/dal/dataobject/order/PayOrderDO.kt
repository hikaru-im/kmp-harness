package im.hikaru.ruoyi.module.pay.dal.dataobject.order

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import kotlinx.datetime.LocalDateTime

class PayOrderDO : TenantBaseDO {
    var id: Long? = null
    var appId: Long? = null
    var channelId: Long? = null
    var channelCode: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var merchantOrderId: String? = null
    var subject: String? = null
    var body: String? = null
    var notifyUrl: String? = null
    var price: Int? = null
    var channelFeeRate: Double? = null
    var channelFeePrice: Int? = null
    var status: Int? = null
    var userIp: String? = null
    var expireTime: LocalDateTime? = null
    var successTime: LocalDateTime? = null
    var extensionId: Long? = null
    var no: String? = null
    var refundPrice: Int? = null
    var channelUserId: String? = null
    var channelOrderNo: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
