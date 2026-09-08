package im.hikaru.ruoyi.module.infra.dal.mysql.config

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.config.ConfigDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

/** Exposed DAO for system configuration entries. */
object ConfigDao {

    fun selectByKey(key: String): ConfigDO? = transaction {
        ConfigTable.selectAll()
            .where {
                listOf(
                        ConfigTable.key eq key,
                        ConfigTable.deleted eq false,
                    ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectById(id: Long): ConfigDO? = transaction {
        ConfigTable.selectAll()
            .where {
                listOf(
                        ConfigTable.id eq id,
                        ConfigTable.deleted eq false,
                    ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<ConfigDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            ConfigTable.selectAll()
                .where {
                    listOf(
                            ConfigTable.id inList ids,
                            ConfigTable.deleted eq false,
                        ).compoundAnd()
                }
                .map(::toEntity)
        }
    }

    fun selectPage(reqVO: ConfigPageReqVO): PageResult<ConfigDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(ConfigTable.deleted eq false)
        reqVO.name?.takeIf(String::isNotEmpty)?.let {
            conditions += ConfigTable.name like "%$it%"
        }
        reqVO.key?.takeIf(String::isNotEmpty)?.let {
            conditions += ConfigTable.key like "%$it%"
        }
        reqVO.type?.let {
            conditions += ConfigTable.type eq it
        }
        reqVO.createTime?.let { (begin, end) ->
            conditions += ConfigTable.createTime greaterEq begin.toKotlinLocalDateTime()
            conditions += ConfigTable.createTime lessEq end.toKotlinLocalDateTime()
        }
        ConfigTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(ConfigTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun insert(entity: ConfigDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val category = requireNotNull(entity.category) { "Config category must not be null" }
        val name = requireNotNull(entity.name) { "Config name must not be null" }
        val key = requireNotNull(entity.key) { "Config key must not be null" }
        val value = requireNotNull(entity.value) { "Config value must not be null" }
        val type = requireNotNull(entity.type) { "Config type must not be null" }
        val visible = requireNotNull(entity.visible) { "Config visibility must not be null" }
        val createTime = requireNotNull(entity.createTime) { "Config createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "Config updateTime must not be null" }
        return transaction {
            ConfigTable.insert {
                it[ConfigTable.category] = category
                it[ConfigTable.name] = name
                it[ConfigTable.key] = key
                it[ConfigTable.value] = value
                it[ConfigTable.type] = type
                it[ConfigTable.visible] = visible
                it[ConfigTable.remark] = entity.remark
                it[ConfigTable.creator] = entity.creator.orEmpty()
                it[ConfigTable.updater] = entity.updater.orEmpty()
                it[ConfigTable.createTime] = createTime
                it[ConfigTable.updateTime] = updateTime
            }.get(ConfigTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: ConfigDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "Config id must not be null" }
        val category = entity.category
        val name = entity.name
        val key = entity.key
        val value = entity.value
        val type = entity.type
        val visible = entity.visible
        val remark = entity.remark
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            ConfigTable.update(where = { ConfigTable.id eq id }) {
                if (category != null) it[ConfigTable.category] = category
                if (name != null) it[ConfigTable.name] = name
                if (key != null) it[ConfigTable.key] = key
                if (value != null) it[ConfigTable.value] = value
                if (type != null) it[ConfigTable.type] = type
                if (visible != null) it[ConfigTable.visible] = visible
                if (remark != null) it[ConfigTable.remark] = remark
                if (updater != null) it[ConfigTable.updater] = updater
                if (updateTime != null) it[ConfigTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        ConfigTable.deleteWhere { ConfigTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction {
            ConfigTable.deleteWhere { ConfigTable.id inList ids }
        }
    }

    private fun toEntity(row: ResultRow): ConfigDO = ConfigDO().apply {
        id = row[ConfigTable.id]
        category = row[ConfigTable.category]
        name = row[ConfigTable.name]
        key = row[ConfigTable.key]
        value = row[ConfigTable.value]
        type = row[ConfigTable.type]
        visible = row[ConfigTable.visible]
        remark = row[ConfigTable.remark]
        createTime = row[ConfigTable.createTime]
        updateTime = row[ConfigTable.updateTime]
        creator = row[ConfigTable.creator]
        updater = row[ConfigTable.updater]
        deleted = row[ConfigTable.deleted]
    }
}
