package im.hikaru.ruoyi.module.system.dal.mysql.logger

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object LoginLogTable : BaseTable("system_login_log") {
    val id = long("id").autoIncrement("system_login_log_seq")
    val logType = long("log_type")
    val traceId = varchar("trace_id", 64)
    val userId = long("user_id")
    val userType = integer("user_type")
    val username = varchar("username", 50)
    val result = integer("result")
    val userIp = varchar("user_ip", 50)
    val userAgent = varchar("user_agent", 512)
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
