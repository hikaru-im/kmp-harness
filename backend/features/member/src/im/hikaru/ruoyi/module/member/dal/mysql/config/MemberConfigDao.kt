package im.hikaru.ruoyi.module.member.dal.mysql.config

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.dal.dataobject.config.MemberConfigDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberConfigDao {
    fun selectById(id: Long): MemberConfigDO? = transaction { MemberConfigTable.selectAll().where { conditions(MemberConfigTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberConfigDO> = if (ids.isEmpty()) emptyList() else transaction { MemberConfigTable.selectAll().where { conditions(MemberConfigTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberConfigDO> = transaction { MemberConfigTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberConfigTable.selectAll().where { conditions() }.count() }
    fun insert(entity: MemberConfigDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberConfigTable.insert {
            it[MemberConfigTable.pointTradeDeductEnable] = entity.pointTradeDeductEnable
            it[MemberConfigTable.pointTradeDeductUnitPrice] = entity.pointTradeDeductUnitPrice
            it[MemberConfigTable.pointTradeDeductMaxPrice] = entity.pointTradeDeductMaxPrice
            it[MemberConfigTable.pointTradeGivePoint] = entity.pointTradeGivePoint
            it[MemberConfigTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberConfigTable.creator] = entity.creator
            it[MemberConfigTable.updater] = entity.updater
            it[MemberConfigTable.createTime] = requireNotNull(entity.createTime)
            it[MemberConfigTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberConfigTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberConfigDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberConfigTable.update(where = { conditions(MemberConfigTable.id eq id) }) {
            entity.pointTradeDeductEnable?.let { value -> it[MemberConfigTable.pointTradeDeductEnable] = value }
            entity.pointTradeDeductUnitPrice?.let { value -> it[MemberConfigTable.pointTradeDeductUnitPrice] = value }
            entity.pointTradeDeductMaxPrice?.let { value -> it[MemberConfigTable.pointTradeDeductMaxPrice] = value }
            entity.pointTradeGivePoint?.let { value -> it[MemberConfigTable.pointTradeGivePoint] = value }
            entity.updater?.let { value -> it[MemberConfigTable.updater] = value }
            it[MemberConfigTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberConfigTable.update(where = { conditions(MemberConfigTable.id eq id) }) { it[MemberConfigTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberConfigTable.update(where = { conditions(MemberConfigTable.id inList ids) }) { it[MemberConfigTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberConfigTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberConfigTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberConfigDO().apply {
        id = row[MemberConfigTable.id]
        pointTradeDeductEnable = row[MemberConfigTable.pointTradeDeductEnable]
        pointTradeDeductUnitPrice = row[MemberConfigTable.pointTradeDeductUnitPrice]
        pointTradeDeductMaxPrice = row[MemberConfigTable.pointTradeDeductMaxPrice]
        pointTradeGivePoint = row[MemberConfigTable.pointTradeGivePoint]
        creator = row[MemberConfigTable.creator]
        createTime = row[MemberConfigTable.createTime]
        updater = row[MemberConfigTable.updater]
        updateTime = row[MemberConfigTable.updateTime]
        deleted = row[MemberConfigTable.deleted]
        tenantId = row[MemberConfigTable.tenantId]
    }
}
