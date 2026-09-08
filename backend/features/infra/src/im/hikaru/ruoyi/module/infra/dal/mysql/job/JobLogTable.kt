package im.hikaru.ruoyi.module.infra.dal.mysql.job

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object JobLogTable : BaseTable("infra_job_log") {
    val id = long("id").autoIncrement("infra_job_log_seq")
    val jobId = long("job_id")
    val handlerName = varchar("handler_name", 64)
    val handlerParam = varchar("handler_param", 255).nullable()
    val executeIndex = integer("execute_index")
    val beginTime = datetime("begin_time")
    val endTime = datetime("end_time").nullable()
    val duration = integer("duration").nullable()
    val status = integer("status")
    val result = varchar("result", 4000).nullable()

    override val primaryKey = PrimaryKey(id)
}
