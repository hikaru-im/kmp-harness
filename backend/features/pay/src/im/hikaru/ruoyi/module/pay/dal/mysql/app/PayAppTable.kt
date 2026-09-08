package im.hikaru.ruoyi.module.pay.dal.mysql.app

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object PayAppTable : BaseTable("pay_app") {
    val id = long("id").autoIncrement("pay_app_seq")
    val appKey = varchar("app_key", 255).nullable()
    val name = varchar("name", 255).nullable()
    val status = integer("status").nullable()
    val remark = varchar("remark", 2048).nullable()
    val orderNotifyUrl = varchar("order_notify_url", 2048).nullable()
    val refundNotifyUrl = varchar("refund_notify_url", 2048).nullable()
    val transferNotifyUrl = varchar("transfer_notify_url", 2048).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
