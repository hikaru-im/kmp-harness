package im.hikaru.ruoyi.module.infra.dal.mysql.logger

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object ApiErrorLogTable : BaseTable("infra_api_error_log") {
    val id = long("id").autoIncrement("infra_api_error_log_seq")
    val traceId = varchar("trace_id", 64)
    val userId = long("user_id")
    val userType = integer("user_type")
    val applicationName = varchar("application_name", 50)
    val requestMethod = varchar("request_method", 16)
    val requestUrl = varchar("request_url", 255)
    val requestParams = varchar("request_params", 8000)
    val userIp = varchar("user_ip", 50)
    val userAgent = varchar("user_agent", 512)
    val exceptionTime = datetime("exception_time")
    val exceptionName = varchar("exception_name", 128)
    val exceptionMessage = text("exception_message")
    val exceptionRootCauseMessage = text("exception_root_cause_message")
    val exceptionStackTrace = text("exception_stack_trace")
    val exceptionClassName = varchar("exception_class_name", 512)
    val exceptionFileName = varchar("exception_file_name", 512)
    val exceptionMethodName = varchar("exception_method_name", 512)
    val exceptionLineNumber = integer("exception_line_number")
    val processStatus = integer("process_status")
    val processTime = datetime("process_time").nullable()
    val processUserId = integer("process_user_id").nullable()
    val tenantId = long("tenant_id")

    override val primaryKey = PrimaryKey(id)
}
