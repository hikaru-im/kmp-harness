package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO
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
import java.time.LocalDateTime

object Demo03StudentDao {
    fun selectById(id: Long): Demo03StudentDO? = transaction {
        Demo03StudentTable.selectAll().where {
            listOf(Demo03StudentTable.id eq id, Demo03StudentTable.deleted eq false).compoundAnd()
        }.singleOrNull()?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<Demo03StudentDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            Demo03StudentTable.selectAll().where {
                listOf(Demo03StudentTable.id inList ids, Demo03StudentTable.deleted eq false).compoundAnd()
            }.map(::toEntity)
        }
    }

    fun selectPage(
        pageParam: PageParam,
        name: String?,
        sex: Int?,
        description: String?,
        createTime: Array<LocalDateTime>?,
    ): PageResult<Demo03StudentDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(Demo03StudentTable.deleted eq false)
        name?.takeIf(String::isNotEmpty)?.let { conditions += Demo03StudentTable.name like "%$it%" }
        sex?.let { conditions += Demo03StudentTable.sex eq it }
        description?.let { conditions += Demo03StudentTable.description eq it }
        createTime?.let { (begin, end) ->
            conditions += Demo03StudentTable.createTime greaterEq begin.toKotlinLocalDateTime()
            conditions += Demo03StudentTable.createTime lessEq end.toKotlinLocalDateTime()
        }
        Demo03StudentTable.selectAll().where { conditions.compoundAnd() }
            .orderBy(Demo03StudentTable.id, SortOrder.DESC)
            .toPageResult(pageParam, ::toEntity)
    }

    fun insert(entity: Demo03StudentDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val name = requireNotNull(entity.name)
        val sex = requireNotNull(entity.sex)
        val birthday = requireNotNull(entity.birthday)
        val description = requireNotNull(entity.description)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            Demo03StudentTable.insert {
                it[Demo03StudentTable.name] = name
                it[Demo03StudentTable.sex] = sex
                it[Demo03StudentTable.birthday] = birthday
                it[Demo03StudentTable.description] = description
                it[Demo03StudentTable.tenantId] = TenantContextHolder.getTenantId() ?: 0L
                it[Demo03StudentTable.creator] = entity.creator.orEmpty()
                it[Demo03StudentTable.updater] = entity.updater.orEmpty()
                it[Demo03StudentTable.createTime] = createTime
                it[Demo03StudentTable.updateTime] = updateTime
            }.get(Demo03StudentTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: Demo03StudentDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        val name = entity.name
        val sex = entity.sex
        val birthday = entity.birthday
        val description = entity.description
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            Demo03StudentTable.update(where = { Demo03StudentTable.id eq id }) {
                if (name != null) it[Demo03StudentTable.name] = name
                if (sex != null) it[Demo03StudentTable.sex] = sex
                if (birthday != null) it[Demo03StudentTable.birthday] = birthday
                if (description != null) it[Demo03StudentTable.description] = description
                if (updater != null) it[Demo03StudentTable.updater] = updater
                if (updateTime != null) it[Demo03StudentTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction { Demo03StudentTable.deleteWhere { Demo03StudentTable.id eq id } }
    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { Demo03StudentTable.deleteWhere { Demo03StudentTable.id inList ids } }
    }

    private fun toEntity(row: ResultRow): Demo03StudentDO = Demo03StudentDO().apply {
        id = row[Demo03StudentTable.id]
        name = row[Demo03StudentTable.name]
        sex = row[Demo03StudentTable.sex]
        birthday = row[Demo03StudentTable.birthday]
        description = row[Demo03StudentTable.description]
        creator = row[Demo03StudentTable.creator]
        createTime = row[Demo03StudentTable.createTime]
        updater = row[Demo03StudentTable.updater]
        updateTime = row[Demo03StudentTable.updateTime]
        deleted = row[Demo03StudentTable.deleted]
    }
}
