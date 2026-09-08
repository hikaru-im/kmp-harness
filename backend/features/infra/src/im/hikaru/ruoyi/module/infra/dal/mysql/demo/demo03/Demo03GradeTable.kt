package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object Demo03GradeTable : BaseTable("yudao_demo03_grade") {
    val id = long("id").autoIncrement("yudao_demo03_grade_seq")
    val studentId = long("student_id")
    val name = varchar("name", 100)
    val teacher = varchar("teacher", 255)
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
