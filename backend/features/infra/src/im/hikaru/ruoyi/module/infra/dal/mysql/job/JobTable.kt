package im.hikaru.ruoyi.module.infra.dal.mysql.job

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object JobTable : BaseTable("infra_job") {
    val id = long("id").autoIncrement("infra_job_seq")
    val name = varchar("name", 32)
    val status = integer("status")
    val handlerName = varchar("handler_name", 64)
    val handlerParam = varchar("handler_param", 255).nullable()
    val cronExpression = varchar("cron_expression", 32)
    val retryCount = integer("retry_count")
    val retryInterval = integer("retry_interval")
    val monitorTimeout = integer("monitor_timeout")

    override val primaryKey = PrimaryKey(id)
}
