package im.hikaru.ruoyi.module.pay.dal.dataobject.order

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import kotlinx.datetime.LocalDateTime

class PayOrderExtensionDO : TenantBaseDO {
    var id: Long? = null
    var no: String? = null
    var orderId: Long? = null
    var channelId: Long? = null
    var channelCode: String? = null
    var userIp: String? = null
    var status: Int? = null
    var channelExtras: Map<String, String>? = null
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
