package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo01

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo01.Demo01ContactDO
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

object Demo01ContactDao {
    fun selectById(id: Long): Demo01ContactDO? = transaction {
        Demo01ContactTable.selectAll()
            .where { listOf(Demo01ContactTable.id eq id, Demo01ContactTable.deleted eq false).compoundAnd() }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<Demo01ContactDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            Demo01ContactTable.selectAll()
                .where { listOf(Demo01ContactTable.id inList ids, Demo01ContactTable.deleted eq false).compoundAnd() }
                .map(::toEntity)
        }
    }

    fun selectPage(reqVO: Demo01ContactPageReqVO): PageResult<Demo01ContactDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(Demo01ContactTable.deleted eq false)
        reqVO.name?.takeIf(String::isNotEmpty)?.let { conditions += Demo01ContactTable.name like "%$it%" }
        reqVO.sex?.let { conditions += Demo01ContactTable.sex eq it }
        reqVO.createTime?.let { (begin, end) ->
            conditions += Demo01ContactTable.createTime greaterEq begin.toKotlinLocalDateTime()
            conditions += Demo01ContactTable.createTime lessEq end.toKotlinLocalDateTime()
        }
        Demo01ContactTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(Demo01ContactTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun insert(entity: Demo01ContactDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val name = requireNotNull(entity.name)
        val sex = requireNotNull(entity.sex)
        val birthday = requireNotNull(entity.birthday)
        val description = requireNotNull(entity.description)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            Demo01ContactTable.insert {
                it[Demo01ContactTable.name] = name
                it[Demo01ContactTable.sex] = sex
                it[Demo01ContactTable.birthday] = birthday
                it[Demo01ContactTable.description] = description
                it[Demo01ContactTable.avatar] = entity.avatar
                it[Demo01ContactTable.tenantId] = TenantContextHolder.getTenantId() ?: 0L
                it[Demo01ContactTable.creator] = entity.creator.orEmpty()
                it[Demo01ContactTable.updater] = entity.updater.orEmpty()
                it[Demo01ContactTable.createTime] = createTime
                it[Demo01ContactTable.updateTime] = updateTime
            }.get(Demo01ContactTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: Demo01ContactDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        val name = entity.name
        val sex = entity.sex
        val birthday = entity.birthday
        val description = entity.description
        val avatar = entity.avatar
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            Demo01ContactTable.update(where = { Demo01ContactTable.id eq id }) {
                if (name != null) it[Demo01ContactTable.name] = name
                if (sex != null) it[Demo01ContactTable.sex] = sex
                if (birthday != null) it[Demo01ContactTable.birthday] = birthday
                if (description != null) it[Demo01ContactTable.description] = description
                if (avatar != null) it[Demo01ContactTable.avatar] = avatar
                if (updater != null) it[Demo01ContactTable.updater] = updater
                if (updateTime != null) it[Demo01ContactTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction { Demo01ContactTable.deleteWhere { Demo01ContactTable.id eq id } }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { Demo01ContactTable.deleteWhere { Demo01ContactTable.id inList ids } }
    }

    private fun toEntity(row: ResultRow): Demo01ContactDO = Demo01ContactDO().apply {
        id = row[Demo01ContactTable.id]
        name = row[Demo01ContactTable.name]
        sex = row[Demo01ContactTable.sex]
        birthday = row[Demo01ContactTable.birthday]
        description = row[Demo01ContactTable.description]
        avatar = row[Demo01ContactTable.avatar]
        creator = row[Demo01ContactTable.creator]
        createTime = row[Demo01ContactTable.createTime]
        updater = row[Demo01ContactTable.updater]
        updateTime = row[Demo01ContactTable.updateTime]
        deleted = row[Demo01ContactTable.deleted]
    }
}
