package im.hikaru.ruoyi.module.infra.dal.mysql.db

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.infra.dal.dataobject.db.DataSourceConfigDO
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object DataSourceConfigDao {

    fun selectById(id: Long): DataSourceConfigDO? = transaction {
        DataSourceConfigTable.selectAll()
            .where {
                listOf(
                    DataSourceConfigTable.id eq id,
                    DataSourceConfigTable.deleted eq false,
                ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<DataSourceConfigDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            DataSourceConfigTable.selectAll()
                .where {
                    listOf(
                        DataSourceConfigTable.id inList ids,
                        DataSourceConfigTable.deleted eq false,
                    ).compoundAnd()
                }
                .map(::toEntity)
        }
    }

    fun selectList(): List<DataSourceConfigDO> = transaction {
        DataSourceConfigTable.selectAll()
            .where { DataSourceConfigTable.deleted eq false }
            .orderBy(DataSourceConfigTable.id, SortOrder.ASC)
            .map(::toEntity)
    }

    fun selectPage(pageParam: PageParam): PageResult<DataSourceConfigDO> = transaction {
        DataSourceConfigTable.selectAll()
            .where { DataSourceConfigTable.deleted eq false }
            .orderBy(DataSourceConfigTable.id, SortOrder.DESC)
            .toPageResult(pageParam, ::toEntity)
    }

    fun insert(entity: DataSourceConfigDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val name = requireNotNull(entity.name) { "Data source name must not be null" }
        val url = requireNotNull(entity.url) { "Data source URL must not be null" }
        val username = requireNotNull(entity.username) { "Data source username must not be null" }
        val password = requireNotNull(entity.password) { "Data source password must not be null" }
        val createTime = requireNotNull(entity.createTime) { "Data source createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "Data source updateTime must not be null" }
        return transaction {
            DataSourceConfigTable.insert {
                it[DataSourceConfigTable.name] = name
                it[DataSourceConfigTable.url] = url
                it[DataSourceConfigTable.username] = username
                it[DataSourceConfigTable.password] = password
                it[DataSourceConfigTable.creator] = entity.creator.orEmpty()
                it[DataSourceConfigTable.updater] = entity.updater.orEmpty()
                it[DataSourceConfigTable.createTime] = createTime
                it[DataSourceConfigTable.updateTime] = updateTime
            }.get(DataSourceConfigTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: DataSourceConfigDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "Data source id must not be null" }
        val name = entity.name
        val url = entity.url
        val username = entity.username
        val password = entity.password
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            DataSourceConfigTable.update(where = { DataSourceConfigTable.id eq id }) {
                if (name != null) it[DataSourceConfigTable.name] = name
                if (url != null) it[DataSourceConfigTable.url] = url
                if (username != null) it[DataSourceConfigTable.username] = username
                if (password != null) it[DataSourceConfigTable.password] = password
                if (updater != null) it[DataSourceConfigTable.updater] = updater
                if (updateTime != null) it[DataSourceConfigTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        DataSourceConfigTable.deleteWhere { DataSourceConfigTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction {
            DataSourceConfigTable.deleteWhere { DataSourceConfigTable.id inList ids }
        }
    }

    private fun toEntity(row: ResultRow): DataSourceConfigDO = DataSourceConfigDO().apply {
        id = row[DataSourceConfigTable.id]
        name = row[DataSourceConfigTable.name]
        url = row[DataSourceConfigTable.url]
        username = row[DataSourceConfigTable.username]
        password = row[DataSourceConfigTable.password]
        createTime = row[DataSourceConfigTable.createTime]
        updateTime = row[DataSourceConfigTable.updateTime]
        creator = row[DataSourceConfigTable.creator]
        updater = row[DataSourceConfigTable.updater]
        deleted = row[DataSourceConfigTable.deleted]
    }
}
