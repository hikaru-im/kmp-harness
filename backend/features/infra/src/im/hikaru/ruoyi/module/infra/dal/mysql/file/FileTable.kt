package im.hikaru.ruoyi.module.infra.dal.mysql.file

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object FileTable : BaseTable("infra_file") {
    val id = long("id").autoIncrement("infra_file_seq")
    val configId = long("config_id").nullable()
    val name = varchar("name", 256).nullable()
    val path = varchar("path", 512)
    val url = varchar("url", 1024)
    val type = varchar("type", 128).nullable()
    val size = integer("size")

    override val primaryKey = PrimaryKey(id)
}
