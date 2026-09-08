package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object Demo03StudentTable : BaseTable("yudao_demo03_student") {
    val id = long("id").autoIncrement("yudao_demo03_student_seq")
    val name = varchar("name", 100)
    val sex = integer("sex")
    val birthday = datetime("birthday")
    val description = varchar("description", 255)
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
