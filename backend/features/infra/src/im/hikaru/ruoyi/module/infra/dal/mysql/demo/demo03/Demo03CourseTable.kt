package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object Demo03CourseTable : BaseTable("yudao_demo03_course") {
    val id = long("id").autoIncrement("yudao_demo03_course_seq")
    val studentId = long("student_id")
    val name = varchar("name", 100)
    val score = integer("score")
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
