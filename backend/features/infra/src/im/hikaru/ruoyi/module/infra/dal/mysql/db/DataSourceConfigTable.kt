package im.hikaru.ruoyi.module.infra.dal.mysql.db

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object DataSourceConfigTable : BaseTable("infra_data_source_config") {
    val id = long("id").autoIncrement("infra_data_source_config_seq")
    val name = varchar("name", 100)
    val url = varchar("url", 1024)
    val username = varchar("username", 255)
    val password = varchar("password", 255)

    override val primaryKey = PrimaryKey(id)
}
