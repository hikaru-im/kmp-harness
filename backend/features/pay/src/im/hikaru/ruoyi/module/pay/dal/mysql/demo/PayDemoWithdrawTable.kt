package im.hikaru.ruoyi.module.pay.dal.mysql.demo

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object PayDemoWithdrawTable : BaseTable("pay_demo_withdraw") {
    val id = long("id").autoIncrement("pay_demo_withdraw_seq")
    val subject = varchar("subject", 255).nullable()
    val price = integer("price").nullable()
    val userAccount = varchar("user_account", 255).nullable()
    val userName = varchar("user_name", 255).nullable()
    val type = integer("type").nullable()
    val status = integer("status").nullable()
    val payTransferId = long("pay_transfer_id").nullable()
    val transferChannelCode = varchar("transfer_channel_code", 128).nullable()
    val transferTime = datetime("transfer_time").nullable()
    val transferErrorMsg = varchar("transfer_error_msg", 255).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
