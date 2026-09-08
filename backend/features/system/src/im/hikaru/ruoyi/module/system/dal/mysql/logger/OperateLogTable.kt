package im.hikaru.ruoyi.module.system.dal.mysql.logger

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object OperateLogTable : BaseTable("system_operate_log") {
    val id = long("id").autoIncrement("system_operate_log_seq")
    val traceId = varchar("trace_id", 64)
    val userId = long("user_id")
    val userType = integer("user_type")
    val type = varchar("type", 50)
    val subType = varchar("sub_type", 50)
    val bizId = long("biz_id")
    val action = varchar("action", 2000)
    val success = bool("success").default(true)
    val extra = varchar("extra", 2000)
    val requestMethod = varchar("request_method", 16).nullable()
    val requestUrl = varchar("request_url", 255).nullable()
    val userIp = varchar("user_ip", 50).nullable()
    val userAgent = varchar("user_agent", 512).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
