package im.hikaru.ruoyi.module.pay.dal.mysql.channel

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.jsonObject
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig

object PayChannelTable : BaseTable("pay_channel") {
    val id = long("id").autoIncrement("pay_channel_seq")
    val code = varchar("code", 128).nullable()
    val status = integer("status").nullable()
    val feeRate = double("fee_rate").nullable()
    val remark = varchar("remark", 2048).nullable()
    val appId = long("app_id").nullable()
    val config = jsonObject("config", PayClientConfig::class.java, 10240).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
