package im.hikaru.ruoyi.module.pay.dal.mysql.demo

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.PayDemoOrderDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayDemoOrderDao {
    fun selectById(id: Long): PayDemoOrderDO? = transaction { PayDemoOrderTable.selectAll().where { conditions(PayDemoOrderTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayDemoOrderDO> = if (ids.isEmpty()) emptyList() else transaction { PayDemoOrderTable.selectAll().where { conditions(PayDemoOrderTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayDemoOrderDO> = transaction { PayDemoOrderTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayDemoOrderTable.selectAll().where { conditions() }.count() }
    fun selectPage(page: PageParam): PageResult<PayDemoOrderDO> = transaction {
        val all = PayDemoOrderTable.selectAll().where { conditions() }.orderBy(PayDemoOrderTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong(); if (page.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + page.pageSize, all.size)))
    }
    fun updateByIdAndPayStatus(id: Long, expected: Boolean, entity: PayDemoOrderDO): Int {
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayDemoOrderTable.update(where = {
            conditions(PayDemoOrderTable.id eq id, PayDemoOrderTable.payStatus eq expected)
        }) {
            entity.payStatus?.let { value -> it[PayDemoOrderTable.payStatus] = value }
            entity.payTime?.let { value -> it[PayDemoOrderTable.payTime] = value }
            entity.payChannelCode?.let { value -> it[PayDemoOrderTable.payChannelCode] = value }
            entity.updater?.let { value -> it[PayDemoOrderTable.updater] = value }
            it[PayDemoOrderTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun insert(entity: PayDemoOrderDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayDemoOrderTable.insert {
            it[PayDemoOrderTable.userId] = entity.userId
            it[PayDemoOrderTable.spuId] = entity.spuId
            it[PayDemoOrderTable.spuName] = entity.spuName
            it[PayDemoOrderTable.price] = entity.price
            it[PayDemoOrderTable.payStatus] = entity.payStatus
            it[PayDemoOrderTable.payOrderId] = entity.payOrderId
            it[PayDemoOrderTable.payTime] = entity.payTime
            it[PayDemoOrderTable.payChannelCode] = entity.payChannelCode
            it[PayDemoOrderTable.payRefundId] = entity.payRefundId
            it[PayDemoOrderTable.refundPrice] = entity.refundPrice
            it[PayDemoOrderTable.refundTime] = entity.refundTime
            it[PayDemoOrderTable.transferChannelPackageInfo] = entity.transferChannelPackageInfo
            it[PayDemoOrderTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayDemoOrderTable.creator] = entity.creator
            it[PayDemoOrderTable.updater] = entity.updater
            it[PayDemoOrderTable.createTime] = requireNotNull(entity.createTime)
            it[PayDemoOrderTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayDemoOrderTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayDemoOrderDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayDemoOrderTable.update(where = { conditions(PayDemoOrderTable.id eq id) }) {
            entity.userId?.let { value -> it[PayDemoOrderTable.userId] = value }
            entity.spuId?.let { value -> it[PayDemoOrderTable.spuId] = value }
            entity.spuName?.let { value -> it[PayDemoOrderTable.spuName] = value }
            entity.price?.let { value -> it[PayDemoOrderTable.price] = value }
            entity.payStatus?.let { value -> it[PayDemoOrderTable.payStatus] = value }
            entity.payOrderId?.let { value -> it[PayDemoOrderTable.payOrderId] = value }
            entity.payTime?.let { value -> it[PayDemoOrderTable.payTime] = value }
            entity.payChannelCode?.let { value -> it[PayDemoOrderTable.payChannelCode] = value }
            entity.payRefundId?.let { value -> it[PayDemoOrderTable.payRefundId] = value }
            entity.refundPrice?.let { value -> it[PayDemoOrderTable.refundPrice] = value }
            entity.refundTime?.let { value -> it[PayDemoOrderTable.refundTime] = value }
            entity.transferChannelPackageInfo?.let { value -> it[PayDemoOrderTable.transferChannelPackageInfo] = value }
            entity.updater?.let { value -> it[PayDemoOrderTable.updater] = value }
            it[PayDemoOrderTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayDemoOrderTable.update(where = { conditions(PayDemoOrderTable.id eq id) }) { it[PayDemoOrderTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayDemoOrderTable.update(where = { conditions(PayDemoOrderTable.id inList ids) }) { it[PayDemoOrderTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayDemoOrderTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayDemoOrderTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayDemoOrderDO().apply {
        id = row[PayDemoOrderTable.id]
        userId = row[PayDemoOrderTable.userId]
        spuId = row[PayDemoOrderTable.spuId]
        spuName = row[PayDemoOrderTable.spuName]
        price = row[PayDemoOrderTable.price]
        payStatus = row[PayDemoOrderTable.payStatus]
        payOrderId = row[PayDemoOrderTable.payOrderId]
        payTime = row[PayDemoOrderTable.payTime]
        payChannelCode = row[PayDemoOrderTable.payChannelCode]
        payRefundId = row[PayDemoOrderTable.payRefundId]
        refundPrice = row[PayDemoOrderTable.refundPrice]
        refundTime = row[PayDemoOrderTable.refundTime]
        transferChannelPackageInfo = row[PayDemoOrderTable.transferChannelPackageInfo]
        creator = row[PayDemoOrderTable.creator]
        createTime = row[PayDemoOrderTable.createTime]
        updater = row[PayDemoOrderTable.updater]
        updateTime = row[PayDemoOrderTable.updateTime]
        deleted = row[PayDemoOrderTable.deleted]
        tenantId = row[PayDemoOrderTable.tenantId]
    }
}
