package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo01

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object Demo01ContactTable : BaseTable("yudao_demo01_contact") {
    val id = long("id").autoIncrement("yudao_demo01_contact_seq")
    val name = varchar("name", 100)
    val sex = integer("sex")
    val birthday = datetime("birthday")
    val description = varchar("description", 255)
    val avatar = varchar("avatar", 512).nullable()
    val tenantId = long("tenant_id")

    override val primaryKey = PrimaryKey(id)
}
