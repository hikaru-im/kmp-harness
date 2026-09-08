package im.hikaru.ruoyi.module.infra.service.db

interface DatabaseTableService {
    fun getTableList(dataSourceConfigId: Long, nameLike: String?, commentLike: String?): List<DatabaseTableInfo>

    fun getTable(dataSourceConfigId: Long, tableName: String): DatabaseTableInfo?
}
