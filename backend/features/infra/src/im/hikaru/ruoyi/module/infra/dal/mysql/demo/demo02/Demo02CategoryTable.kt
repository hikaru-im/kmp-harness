package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo02

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object Demo02CategoryTable : BaseTable("yudao_demo02_category") {
    val id = long("id").autoIncrement("yudao_demo02_category_seq")
    val name = varchar("name", 100)
    val parentId = long("parent_id")
    val tenantId = long("tenant_id")

    override val primaryKey = PrimaryKey(id)
}
