package im.hikaru.ruoyi.module.pay.dal.mysql.notify

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object PayNotifyLogTable : BaseTable("pay_notify_log") {
    val id = long("id").autoIncrement("pay_notify_log_seq")
    val taskId = long("task_id").nullable()
    val notifyTimes = integer("notify_times").nullable()
    val response = varchar("response", 2048).nullable()
    val status = integer("status").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
