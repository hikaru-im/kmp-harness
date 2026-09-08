package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo02

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategoryListReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo02.Demo02CategoryDO
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

object Demo02CategoryDao {

    fun selectById(id: Long): Demo02CategoryDO? = transaction {
        Demo02CategoryTable.selectAll()
            .where { listOf(Demo02CategoryTable.id eq id, Demo02CategoryTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<Demo02CategoryDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            Demo02CategoryTable.selectAll()
                .where { listOf(Demo02CategoryTable.id inList ids, Demo02CategoryTable.deleted eq false).compoundAnd() }
                .map(::toEntity)
        }
    }

    fun selectList(reqVO: Demo02CategoryListReqVO): List<Demo02CategoryDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(Demo02CategoryTable.deleted eq false)
        reqVO.name?.takeIf(String::isNotEmpty)?.let { conditions += Demo02CategoryTable.name like "%$it%" }
        reqVO.parentId?.let { conditions += Demo02CategoryTable.parentId eq it }
        reqVO.createTime?.let { (begin, end) ->
            conditions += Demo02CategoryTable.createTime greaterEq begin.toKotlinLocalDateTime()
            conditions += Demo02CategoryTable.createTime lessEq end.toKotlinLocalDateTime()
        }
        Demo02CategoryTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(Demo02CategoryTable.id, SortOrder.DESC)
            .map(::toEntity)
    }

    fun selectByParentIdAndName(parentId: Long?, name: String): Demo02CategoryDO? = transaction {
        val parentCondition = if (parentId == null) {
            Demo02CategoryTable.parentId eq Demo02CategoryDO.PARENT_ID_ROOT
        } else {
            Demo02CategoryTable.parentId eq parentId
        }
        Demo02CategoryTable.selectAll()
            .where {
                listOf(
                    parentCondition,
                    Demo02CategoryTable.name eq name,
                    Demo02CategoryTable.deleted eq false,
                ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectCountByParentId(parentId: Long): Long = transaction {
        Demo02CategoryTable.selectAll()
            .where { listOf(Demo02CategoryTable.parentId eq parentId, Demo02CategoryTable.deleted eq false).compoundAnd() }
            .count()
    }

    fun insert(entity: Demo02CategoryDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val name = requireNotNull(entity.name)
        val parentId = requireNotNull(entity.parentId)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            Demo02CategoryTable.insert {
                it[Demo02CategoryTable.name] = name
                it[Demo02CategoryTable.parentId] = parentId
                it[Demo02CategoryTable.tenantId] = TenantContextHolder.getTenantId() ?: 0L
                it[Demo02CategoryTable.creator] = entity.creator.orEmpty()
                it[Demo02CategoryTable.updater] = entity.updater.orEmpty()
                it[Demo02CategoryTable.createTime] = createTime
                it[Demo02CategoryTable.updateTime] = updateTime
            }.get(Demo02CategoryTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: Demo02CategoryDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        val name = entity.name
        val parentId = entity.parentId
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            Demo02CategoryTable.update(where = { Demo02CategoryTable.id eq id }) {
                if (name != null) it[Demo02CategoryTable.name] = name
                if (parentId != null) it[Demo02CategoryTable.parentId] = parentId
                if (updater != null) it[Demo02CategoryTable.updater] = updater
                if (updateTime != null) it[Demo02CategoryTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction { Demo02CategoryTable.deleteWhere { Demo02CategoryTable.id eq id } }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { Demo02CategoryTable.deleteWhere { Demo02CategoryTable.id inList ids } }
    }

    private fun toEntity(row: ResultRow): Demo02CategoryDO = Demo02CategoryDO().apply {
        id = row[Demo02CategoryTable.id]
        name = row[Demo02CategoryTable.name]
        parentId = row[Demo02CategoryTable.parentId]
        creator = row[Demo02CategoryTable.creator]
        createTime = row[Demo02CategoryTable.createTime]
        updater = row[Demo02CategoryTable.updater]
        updateTime = row[Demo02CategoryTable.updateTime]
        deleted = row[Demo02CategoryTable.deleted]
    }
}
