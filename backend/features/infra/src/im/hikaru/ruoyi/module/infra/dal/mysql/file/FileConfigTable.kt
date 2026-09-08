package im.hikaru.ruoyi.module.infra.dal.mysql.file

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object FileConfigTable : BaseTable("infra_file_config") {
    val id = long("id").autoIncrement("infra_file_config_seq")
    val name = varchar("name", 63)
    val storage = integer("storage")
    val remark = varchar("remark", 255).nullable()
    val master = bool("master")
    val config = varchar("config", 4096)

    override val primaryKey = PrimaryKey(id)
}
