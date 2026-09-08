package im.hikaru.ruoyi.module.pay.dal.dataobject.transfer

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import kotlinx.datetime.LocalDateTime

class PayTransferDO : TenantBaseDO {
    var id: Long? = null
    var no: String? = null
    var appId: Long? = null
    var channelId: Long? = null
    var channelCode: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var merchantTransferId: String? = null
    var subject: String? = null
    var price: Int? = null
    var userAccount: String? = null
    var userName: String? = null
    var status: Int? = null
    var successTime: LocalDateTime? = null
    var notifyUrl: String? = null
    var userIp: String? = null
    var channelExtras: Map<String, String>? = null
    var channelTransferNo: String? = null
    var channelErrorCode: String? = null
    var channelErrorMsg: String? = null
    var channelNotifyData: String? = null
    var channelPackageInfo: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
