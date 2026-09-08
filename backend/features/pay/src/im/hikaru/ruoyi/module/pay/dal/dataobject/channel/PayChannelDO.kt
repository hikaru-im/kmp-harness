package im.hikaru.ruoyi.module.pay.dal.dataobject.channel

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.NonePayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.weixin.WxPayClientConfig
import java.lang.reflect.Field
import kotlinx.datetime.LocalDateTime
import tools.jackson.core.type.TypeReference

class PayChannelDO : TenantBaseDO {
    var id: Long? = null
    var code: String? = null
    var status: Int? = null
    var feeRate: Double? = null
    var remark: String? = null
    var appId: Long? = null
    var config: PayClientConfig? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
