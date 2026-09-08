package im.hikaru.ruoyi.module.pay.dal.mysql.notify

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object PayNotifyTaskTable : BaseTable("pay_notify_task") {
    val id = long("id").autoIncrement("pay_notify_task_seq")
    val appId = long("app_id").nullable()
    val type = integer("type").nullable()
    val dataId = long("data_id").nullable()
    val merchantOrderId = varchar("merchant_order_id", 128).nullable()
    val merchantRefundId = varchar("merchant_refund_id", 128).nullable()
    val merchantTransferId = varchar("merchant_transfer_id", 128).nullable()
    val status = integer("status").nullable()
    val nextNotifyTime = datetime("next_notify_time").nullable()
    val lastExecuteTime = datetime("last_execute_time").nullable()
    val notifyTimes = integer("notify_times").nullable()
    val maxNotifyTimes = integer("max_notify_times").nullable()
    val notifyUrl = varchar("notify_url", 2048).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
