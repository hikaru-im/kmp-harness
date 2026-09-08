package im.hikaru.ruoyi.module.infra.dal.mysql.file

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object FileContentTable : BaseTable("infra_file_content") {
    val id = long("id").autoIncrement("infra_file_content_seq")
    val configId = long("config_id")
    val path = varchar("path", 512)
    val content = binary("content")

    override val primaryKey = PrimaryKey(id)
}
