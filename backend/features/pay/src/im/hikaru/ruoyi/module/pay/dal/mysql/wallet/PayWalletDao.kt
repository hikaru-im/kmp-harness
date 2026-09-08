package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletPageReqVO
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayWalletDao {
    fun selectById(id: Long): PayWalletDO? = transaction { PayWalletTable.selectAll().where { conditions(PayWalletTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByUserIdAndType(userId: Long, userType: Int): PayWalletDO? = transaction {
        PayWalletTable.selectAll().where { conditions(PayWalletTable.userId eq userId, PayWalletTable.userType eq userType) }.singleOrNull()?.let(::toEntity)
    }
    fun selectByIds(ids: Collection<Long>): List<PayWalletDO> = if (ids.isEmpty()) emptyList() else transaction { PayWalletTable.selectAll().where { conditions(PayWalletTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayWalletDO> = transaction { PayWalletTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayWalletTable.selectAll().where { conditions() }.count() }
    fun selectPage(req: PayWalletPageReqVO): PageResult<PayWalletDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.userId?.let { ops += PayWalletTable.userId eq it }
        req.userType?.let { ops += PayWalletTable.userType eq it }
        req.createTime?.getOrNull(0)?.let { ops += PayWalletTable.createTime greaterEq it.toKotlinLocalDateTime() }
        req.createTime?.getOrNull(1)?.let { ops += PayWalletTable.createTime lessEq it.toKotlinLocalDateTime() }
        val all = PayWalletTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(PayWalletTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong(); if (req.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((req.pageNo - 1) * req.pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + req.pageSize, all.size)))
    }
    /** Atomically applies a balance delta and returns the affected row count. */
    fun updateBalance(id: Long, delta: Int): Int = transaction {
        val ops = mutableListOf<Op<Boolean>>(PayWalletTable.id eq id)
        if (delta < 0) ops += PayWalletTable.balance greaterEq -delta
        PayWalletTable.update(where = { conditions(*ops.toTypedArray()) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + delta
            if (delta < 0) it[PayWalletTable.totalExpense] = coalesce(PayWalletTable.totalExpense, intLiteral(0)) + (-delta)
            if (delta > 0) it[PayWalletTable.totalRecharge] = coalesce(PayWalletTable.totalRecharge, intLiteral(0)) + delta
        }
    }
    fun updateFreeze(id: Long, delta: Int): Int = transaction {
        val ops = mutableListOf<Op<Boolean>>(PayWalletTable.id eq id)
        if (delta < 0) ops += PayWalletTable.freezePrice greaterEq -delta
        PayWalletTable.update(where = { conditions(*ops.toTypedArray()) }) {
            it[PayWalletTable.freezePrice] = coalesce(PayWalletTable.freezePrice, intLiteral(0)) + delta
        }
    }
    fun updateWhenConsumption(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = { conditions(PayWalletTable.id eq id, PayWalletTable.balance greaterEq price) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + (-price)
            it[PayWalletTable.totalExpense] = coalesce(PayWalletTable.totalExpense, intLiteral(0)) + price
        }
    }
    fun updateWhenConsumptionRefund(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = { conditions(PayWalletTable.id eq id) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + price
            it[PayWalletTable.totalExpense] = coalesce(PayWalletTable.totalExpense, intLiteral(0)) + (-price)
        }
    }
    fun updateWhenRecharge(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = { conditions(PayWalletTable.id eq id) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + price
            it[PayWalletTable.totalRecharge] = coalesce(PayWalletTable.totalRecharge, intLiteral(0)) + price
        }
    }
    fun updateWhenAdd(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = { conditions(PayWalletTable.id eq id) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + price
        }
    }
    fun updateWhenSubtract(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = { conditions(PayWalletTable.id eq id, PayWalletTable.balance greaterEq price) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + (-price)
        }
    }
    fun freezePrice(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = { conditions(PayWalletTable.id eq id, PayWalletTable.balance greaterEq price) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + (-price)
            it[PayWalletTable.freezePrice] = coalesce(PayWalletTable.freezePrice, intLiteral(0)) + price
        }
    }
    fun unfreezePrice(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = { conditions(PayWalletTable.id eq id, PayWalletTable.freezePrice greaterEq price) }) {
            it[PayWalletTable.balance] = coalesce(PayWalletTable.balance, intLiteral(0)) + price
            it[PayWalletTable.freezePrice] = coalesce(PayWalletTable.freezePrice, intLiteral(0)) + (-price)
        }
    }
    fun updateWhenRechargeRefund(id: Long, price: Int): Int = transaction {
        PayWalletTable.update(where = {
            conditions(
                PayWalletTable.id eq id,
                PayWalletTable.freezePrice greaterEq price,
                PayWalletTable.totalRecharge greaterEq price,
            )
        }) {
            it[PayWalletTable.freezePrice] = coalesce(PayWalletTable.freezePrice, intLiteral(0)) + (-price)
            it[PayWalletTable.totalRecharge] = coalesce(PayWalletTable.totalRecharge, intLiteral(0)) + (-price)
        }
    }
    fun insert(entity: PayWalletDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayWalletTable.insert {
            it[PayWalletTable.userId] = entity.userId
            it[PayWalletTable.userType] = entity.userType
            it[PayWalletTable.balance] = entity.balance
            it[PayWalletTable.freezePrice] = entity.freezePrice
            it[PayWalletTable.totalExpense] = entity.totalExpense
            it[PayWalletTable.totalRecharge] = entity.totalRecharge
            it[PayWalletTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayWalletTable.creator] = entity.creator
            it[PayWalletTable.updater] = entity.updater
            it[PayWalletTable.createTime] = requireNotNull(entity.createTime)
            it[PayWalletTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayWalletTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayWalletDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayWalletTable.update(where = { conditions(PayWalletTable.id eq id) }) {
            entity.userId?.let { value -> it[PayWalletTable.userId] = value }
            entity.userType?.let { value -> it[PayWalletTable.userType] = value }
            entity.balance?.let { value -> it[PayWalletTable.balance] = value }
            entity.freezePrice?.let { value -> it[PayWalletTable.freezePrice] = value }
            entity.totalExpense?.let { value -> it[PayWalletTable.totalExpense] = value }
            entity.totalRecharge?.let { value -> it[PayWalletTable.totalRecharge] = value }
            entity.updater?.let { value -> it[PayWalletTable.updater] = value }
            it[PayWalletTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayWalletTable.update(where = { conditions(PayWalletTable.id eq id) }) { it[PayWalletTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayWalletTable.update(where = { conditions(PayWalletTable.id inList ids) }) { it[PayWalletTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayWalletTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayWalletTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayWalletDO().apply {
        id = row[PayWalletTable.id]
        userId = row[PayWalletTable.userId]
        userType = row[PayWalletTable.userType]
        balance = row[PayWalletTable.balance]
        freezePrice = row[PayWalletTable.freezePrice]
        totalExpense = row[PayWalletTable.totalExpense]
        totalRecharge = row[PayWalletTable.totalRecharge]
        creator = row[PayWalletTable.creator]
        createTime = row[PayWalletTable.createTime]
        updater = row[PayWalletTable.updater]
        updateTime = row[PayWalletTable.updateTime]
        deleted = row[PayWalletTable.deleted]
        tenantId = row[PayWalletTable.tenantId]
    }
}
