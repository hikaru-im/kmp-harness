package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargeDO
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayWalletRechargeDao {
    fun selectById(id: Long): PayWalletRechargeDO? = transaction { PayWalletRechargeTable.selectAll().where { conditions(PayWalletRechargeTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayWalletRechargeDO> = if (ids.isEmpty()) emptyList() else transaction { PayWalletRechargeTable.selectAll().where { conditions(PayWalletRechargeTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayWalletRechargeDO> = transaction { PayWalletRechargeTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayWalletRechargeTable.selectAll().where { conditions() }.count() }
    fun selectPage(walletId: Long, payStatus: Boolean?, page: PageParam): PageResult<PayWalletRechargeDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(PayWalletRechargeTable.walletId eq walletId); payStatus?.let { ops += PayWalletRechargeTable.payStatus eq it }
        val all = PayWalletRechargeTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PayWalletRechargeTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong(); if (page.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0); PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + page.pageSize, all.size)))
    }
    fun updateByIdAndPayStatus(id: Long, expected: Boolean, entity: PayWalletRechargeDO): Int {
        entity.id = id; DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayWalletRechargeTable.update(where = { conditions(PayWalletRechargeTable.id eq id, PayWalletRechargeTable.payStatus eq expected) }) {
            entity.payStatus?.let { value -> it[PayWalletRechargeTable.payStatus] = value }; entity.payOrderId?.let { value -> it[PayWalletRechargeTable.payOrderId] = value }; entity.payChannelCode?.let { value -> it[PayWalletRechargeTable.payChannelCode] = value }; entity.payTime?.let { value -> it[PayWalletRechargeTable.payTime] = value }; entity.payRefundId?.let { value -> it[PayWalletRechargeTable.payRefundId] = value }; entity.refundStatus?.let { value -> it[PayWalletRechargeTable.refundStatus] = value }; entity.refundTime?.let { value -> it[PayWalletRechargeTable.refundTime] = value }; entity.refundTotalPrice?.let { value -> it[PayWalletRechargeTable.refundTotalPrice] = value }; entity.refundPayPrice?.let { value -> it[PayWalletRechargeTable.refundPayPrice] = value }; entity.refundBonusPrice?.let { value -> it[PayWalletRechargeTable.refundBonusPrice] = value }; entity.updater?.let { value -> it[PayWalletRechargeTable.updater] = value }; it[PayWalletRechargeTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun updateByIdAndRefundStatus(id: Long, expected: Int, entity: PayWalletRechargeDO): Int {
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayWalletRechargeTable.update(where = {
            conditions(PayWalletRechargeTable.id eq id, PayWalletRechargeTable.refundStatus eq expected)
        }) {
            entity.refundStatus?.let { value -> it[PayWalletRechargeTable.refundStatus] = value }
            entity.refundTime?.let { value -> it[PayWalletRechargeTable.refundTime] = value }
            entity.refundTotalPrice?.let { value -> it[PayWalletRechargeTable.refundTotalPrice] = value }
            entity.refundPayPrice?.let { value -> it[PayWalletRechargeTable.refundPayPrice] = value }
            entity.refundBonusPrice?.let { value -> it[PayWalletRechargeTable.refundBonusPrice] = value }
            entity.updater?.let { value -> it[PayWalletRechargeTable.updater] = value }
            it[PayWalletRechargeTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun insert(entity: PayWalletRechargeDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayWalletRechargeTable.insert {
            it[PayWalletRechargeTable.walletId] = entity.walletId
            it[PayWalletRechargeTable.totalPrice] = entity.totalPrice
            it[PayWalletRechargeTable.payPrice] = entity.payPrice
            it[PayWalletRechargeTable.bonusPrice] = entity.bonusPrice
            it[PayWalletRechargeTable.packageId] = entity.packageId
            it[PayWalletRechargeTable.payStatus] = entity.payStatus
            it[PayWalletRechargeTable.payOrderId] = entity.payOrderId
            it[PayWalletRechargeTable.payChannelCode] = entity.payChannelCode
            it[PayWalletRechargeTable.payTime] = entity.payTime
            it[PayWalletRechargeTable.payRefundId] = entity.payRefundId
            it[PayWalletRechargeTable.refundTotalPrice] = entity.refundTotalPrice
            it[PayWalletRechargeTable.refundPayPrice] = entity.refundPayPrice
            it[PayWalletRechargeTable.refundBonusPrice] = entity.refundBonusPrice
            it[PayWalletRechargeTable.refundTime] = entity.refundTime
            it[PayWalletRechargeTable.refundStatus] = entity.refundStatus
            it[PayWalletRechargeTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayWalletRechargeTable.creator] = entity.creator
            it[PayWalletRechargeTable.updater] = entity.updater
            it[PayWalletRechargeTable.createTime] = requireNotNull(entity.createTime)
            it[PayWalletRechargeTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayWalletRechargeTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayWalletRechargeDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayWalletRechargeTable.update(where = { conditions(PayWalletRechargeTable.id eq id) }) {
            entity.walletId?.let { value -> it[PayWalletRechargeTable.walletId] = value }
            entity.totalPrice?.let { value -> it[PayWalletRechargeTable.totalPrice] = value }
            entity.payPrice?.let { value -> it[PayWalletRechargeTable.payPrice] = value }
            entity.bonusPrice?.let { value -> it[PayWalletRechargeTable.bonusPrice] = value }
            entity.packageId?.let { value -> it[PayWalletRechargeTable.packageId] = value }
            entity.payStatus?.let { value -> it[PayWalletRechargeTable.payStatus] = value }
            entity.payOrderId?.let { value -> it[PayWalletRechargeTable.payOrderId] = value }
            entity.payChannelCode?.let { value -> it[PayWalletRechargeTable.payChannelCode] = value }
            entity.payTime?.let { value -> it[PayWalletRechargeTable.payTime] = value }
            entity.payRefundId?.let { value -> it[PayWalletRechargeTable.payRefundId] = value }
            entity.refundTotalPrice?.let { value -> it[PayWalletRechargeTable.refundTotalPrice] = value }
            entity.refundPayPrice?.let { value -> it[PayWalletRechargeTable.refundPayPrice] = value }
            entity.refundBonusPrice?.let { value -> it[PayWalletRechargeTable.refundBonusPrice] = value }
            entity.refundTime?.let { value -> it[PayWalletRechargeTable.refundTime] = value }
            entity.refundStatus?.let { value -> it[PayWalletRechargeTable.refundStatus] = value }
            entity.updater?.let { value -> it[PayWalletRechargeTable.updater] = value }
            it[PayWalletRechargeTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayWalletRechargeTable.update(where = { conditions(PayWalletRechargeTable.id eq id) }) { it[PayWalletRechargeTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayWalletRechargeTable.update(where = { conditions(PayWalletRechargeTable.id inList ids) }) { it[PayWalletRechargeTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayWalletRechargeTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayWalletRechargeTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayWalletRechargeDO().apply {
        id = row[PayWalletRechargeTable.id]
        walletId = row[PayWalletRechargeTable.walletId]
        totalPrice = row[PayWalletRechargeTable.totalPrice]
        payPrice = row[PayWalletRechargeTable.payPrice]
        bonusPrice = row[PayWalletRechargeTable.bonusPrice]
        packageId = row[PayWalletRechargeTable.packageId]
        payStatus = row[PayWalletRechargeTable.payStatus]
        payOrderId = row[PayWalletRechargeTable.payOrderId]
        payChannelCode = row[PayWalletRechargeTable.payChannelCode]
        payTime = row[PayWalletRechargeTable.payTime]
        payRefundId = row[PayWalletRechargeTable.payRefundId]
        refundTotalPrice = row[PayWalletRechargeTable.refundTotalPrice]
        refundPayPrice = row[PayWalletRechargeTable.refundPayPrice]
        refundBonusPrice = row[PayWalletRechargeTable.refundBonusPrice]
        refundTime = row[PayWalletRechargeTable.refundTime]
        refundStatus = row[PayWalletRechargeTable.refundStatus]
        creator = row[PayWalletRechargeTable.creator]
        createTime = row[PayWalletRechargeTable.createTime]
        updater = row[PayWalletRechargeTable.updater]
        updateTime = row[PayWalletRechargeTable.updateTime]
        deleted = row[PayWalletRechargeTable.deleted]
        tenantId = row[PayWalletRechargeTable.tenantId]
    }
}
