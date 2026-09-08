package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction.PayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionPageReqVO.Companion.TYPE_EXPENSE
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionPageReqVO.Companion.TYPE_INCOME
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletTransactionDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayWalletTransactionDao {
    fun selectById(id: Long): PayWalletTransactionDO? = transaction { PayWalletTransactionTable.selectAll().where { conditions(PayWalletTransactionTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByNo(no: String): PayWalletTransactionDO? = transaction { PayWalletTransactionTable.selectAll().where { conditions(PayWalletTransactionTable.no eq no) }.singleOrNull()?.let(::toEntity) }
    fun selectByBizIdAndType(bizId: String, type: Int): PayWalletTransactionDO? = transaction { PayWalletTransactionTable.selectAll().where { conditions(PayWalletTransactionTable.bizId eq bizId, PayWalletTransactionTable.bizType eq type) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayWalletTransactionDO> = if (ids.isEmpty()) emptyList() else transaction { PayWalletTransactionTable.selectAll().where { conditions(PayWalletTransactionTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayWalletTransactionDO> = transaction { PayWalletTransactionTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectList(walletId: Long, createTime: Array<java.time.LocalDateTime>?): List<PayWalletTransactionDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(PayWalletTransactionTable.walletId eq walletId)
        createTime?.getOrNull(0)?.let { ops += PayWalletTransactionTable.createTime greaterEq it.toKotlinLocalDateTime() }
        createTime?.getOrNull(1)?.let { ops += PayWalletTransactionTable.createTime lessEq it.toKotlinLocalDateTime() }
        PayWalletTransactionTable.selectAll().where { conditions(*ops.toTypedArray()) }.map(::toEntity)
    }
    fun selectCount(): Long = transaction { PayWalletTransactionTable.selectAll().where { conditions() }.count() }
    fun selectPage(userId: Long?, userType: Int?, walletId: Long?, type: Int?, createTime: Array<java.time.LocalDateTime>?, pageNo: Int, pageSize: Int): PageResult<PayWalletTransactionDO> = transaction {
        val walletIds = walletId?.let { setOf(it) }
        val ops = mutableListOf<Op<Boolean>>()
        walletIds?.let { ops += PayWalletTransactionTable.walletId inList it }
        when (type) {
            TYPE_INCOME -> ops += PayWalletTransactionTable.price greater 0
            TYPE_EXPENSE -> ops += PayWalletTransactionTable.price less 0
        }
        createTime?.getOrNull(0)?.let { ops += PayWalletTransactionTable.createTime greaterEq it.toKotlinLocalDateTime() }
        createTime?.getOrNull(1)?.let { ops += PayWalletTransactionTable.createTime lessEq it.toKotlinLocalDateTime() }
        // User filters are resolved by the wallet service before this method; walletId remains the indexed filter.
        val all = PayWalletTransactionTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PayWalletTransactionTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong(); if (pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((pageNo - 1) * pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + pageSize, all.size)))
    }
    fun insert(entity: PayWalletTransactionDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayWalletTransactionTable.insert {
            it[PayWalletTransactionTable.no] = entity.no
            it[PayWalletTransactionTable.walletId] = entity.walletId
            it[PayWalletTransactionTable.bizType] = entity.bizType
            it[PayWalletTransactionTable.bizId] = entity.bizId
            it[PayWalletTransactionTable.title] = entity.title
            it[PayWalletTransactionTable.price] = entity.price
            it[PayWalletTransactionTable.balance] = entity.balance
            it[PayWalletTransactionTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayWalletTransactionTable.creator] = entity.creator
            it[PayWalletTransactionTable.updater] = entity.updater
            it[PayWalletTransactionTable.createTime] = requireNotNull(entity.createTime)
            it[PayWalletTransactionTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayWalletTransactionTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayWalletTransactionDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayWalletTransactionTable.update(where = { conditions(PayWalletTransactionTable.id eq id) }) {
            entity.no?.let { value -> it[PayWalletTransactionTable.no] = value }
            entity.walletId?.let { value -> it[PayWalletTransactionTable.walletId] = value }
            entity.bizType?.let { value -> it[PayWalletTransactionTable.bizType] = value }
            entity.bizId?.let { value -> it[PayWalletTransactionTable.bizId] = value }
            entity.title?.let { value -> it[PayWalletTransactionTable.title] = value }
            entity.price?.let { value -> it[PayWalletTransactionTable.price] = value }
            entity.balance?.let { value -> it[PayWalletTransactionTable.balance] = value }
            entity.updater?.let { value -> it[PayWalletTransactionTable.updater] = value }
            it[PayWalletTransactionTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayWalletTransactionTable.update(where = { conditions(PayWalletTransactionTable.id eq id) }) { it[PayWalletTransactionTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayWalletTransactionTable.update(where = { conditions(PayWalletTransactionTable.id inList ids) }) { it[PayWalletTransactionTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayWalletTransactionTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayWalletTransactionTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayWalletTransactionDO().apply {
        id = row[PayWalletTransactionTable.id]
        no = row[PayWalletTransactionTable.no]
        walletId = row[PayWalletTransactionTable.walletId]
        bizType = row[PayWalletTransactionTable.bizType]
        bizId = row[PayWalletTransactionTable.bizId]
        title = row[PayWalletTransactionTable.title]
        price = row[PayWalletTransactionTable.price]
        balance = row[PayWalletTransactionTable.balance]
        creator = row[PayWalletTransactionTable.creator]
        createTime = row[PayWalletTransactionTable.createTime]
        updater = row[PayWalletTransactionTable.updater]
        updateTime = row[PayWalletTransactionTable.updateTime]
        deleted = row[PayWalletTransactionTable.deleted]
        tenantId = row[PayWalletTransactionTable.tenantId]
    }
}
