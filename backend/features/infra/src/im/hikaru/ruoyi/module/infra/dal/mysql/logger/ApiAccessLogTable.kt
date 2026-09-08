package im.hikaru.ruoyi.module.infra.dal.mysql.logger

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object ApiAccessLogTable : BaseTable("infra_api_access_log") {
    val id = long("id").autoIncrement("infra_api_access_log_seq")
    val traceId = varchar("trace_id", 64)
    val userId = long("user_id")
    val userType = integer("user_type")
    val applicationName = varchar("application_name", 50)
    val requestMethod = varchar("request_method", 16)
    val requestUrl = varchar("request_url", 255)
    val requestParams = text("request_params").nullable()
    val responseBody = text("response_body").nullable()
    val userIp = varchar("user_ip", 50)
    val userAgent = varchar("user_agent", 512)
    val operateModule = varchar("operate_module", 50).nullable()
    val operateName = varchar("operate_name", 50).nullable()
    val operateType = integer("operate_type").nullable()
    val beginTime = datetime("begin_time")
    val endTime = datetime("end_time")
    val duration = integer("duration")
    val resultCode = integer("result_code")
    val resultMsg = varchar("result_msg", 512).nullable()
    val tenantId = long("tenant_id")

    override val primaryKey = PrimaryKey(id)
}
