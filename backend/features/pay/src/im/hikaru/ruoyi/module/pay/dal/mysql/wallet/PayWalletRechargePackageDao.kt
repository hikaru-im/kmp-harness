package im.hikaru.ruoyi.module.pay.dal.mysql.wallet

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackagePageReqVO
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargePackageDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayWalletRechargePackageDao {
    fun selectById(id: Long): PayWalletRechargePackageDO? = transaction { PayWalletRechargePackageTable.selectAll().where { conditions(PayWalletRechargePackageTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayWalletRechargePackageDO> = if (ids.isEmpty()) emptyList() else transaction { PayWalletRechargePackageTable.selectAll().where { conditions(PayWalletRechargePackageTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayWalletRechargePackageDO> = transaction { PayWalletRechargePackageTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayWalletRechargePackageTable.selectAll().where { conditions() }.count() }
    fun selectByName(name: String): PayWalletRechargePackageDO? = transaction { PayWalletRechargePackageTable.selectAll().where { conditions(PayWalletRechargePackageTable.name eq name) }.singleOrNull()?.let(::toEntity) }
    fun selectListByStatus(status: Int): List<PayWalletRechargePackageDO> = transaction { PayWalletRechargePackageTable.selectAll().where { conditions(PayWalletRechargePackageTable.status eq status) }.orderBy(PayWalletRechargePackageTable.id, SortOrder.ASC).map(::toEntity) }
    fun selectPage(req: WalletRechargePackagePageReqVO): PageResult<PayWalletRechargePackageDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.name?.takeIf(String::isNotBlank)?.let { ops += PayWalletRechargePackageTable.name like "%$it%" }
        req.status?.let { ops += PayWalletRechargePackageTable.status eq it }
        req.createTime?.getOrNull(0)?.let { ops += PayWalletRechargePackageTable.createTime greaterEq it.toKotlinLocalDateTime() }
        req.createTime?.getOrNull(1)?.let { ops += PayWalletRechargePackageTable.createTime lessEq it.toKotlinLocalDateTime() }
        val all = PayWalletRechargePackageTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PayWalletRechargePackageTable.payPrice, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong(); if (req.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((req.pageNo - 1) * req.pageSize).coerceAtLeast(0); PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + req.pageSize, all.size)))
    }
    fun insert(entity: PayWalletRechargePackageDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayWalletRechargePackageTable.insert {
            it[PayWalletRechargePackageTable.name] = entity.name
            it[PayWalletRechargePackageTable.payPrice] = entity.payPrice
            it[PayWalletRechargePackageTable.bonusPrice] = entity.bonusPrice
            it[PayWalletRechargePackageTable.status] = entity.status
            it[PayWalletRechargePackageTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayWalletRechargePackageTable.creator] = entity.creator
            it[PayWalletRechargePackageTable.updater] = entity.updater
            it[PayWalletRechargePackageTable.createTime] = requireNotNull(entity.createTime)
            it[PayWalletRechargePackageTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayWalletRechargePackageTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayWalletRechargePackageDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayWalletRechargePackageTable.update(where = { conditions(PayWalletRechargePackageTable.id eq id) }) {
            entity.name?.let { value -> it[PayWalletRechargePackageTable.name] = value }
            entity.payPrice?.let { value -> it[PayWalletRechargePackageTable.payPrice] = value }
            entity.bonusPrice?.let { value -> it[PayWalletRechargePackageTable.bonusPrice] = value }
            entity.status?.let { value -> it[PayWalletRechargePackageTable.status] = value }
            entity.updater?.let { value -> it[PayWalletRechargePackageTable.updater] = value }
            it[PayWalletRechargePackageTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayWalletRechargePackageTable.update(where = { conditions(PayWalletRechargePackageTable.id eq id) }) { it[PayWalletRechargePackageTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayWalletRechargePackageTable.update(where = { conditions(PayWalletRechargePackageTable.id inList ids) }) { it[PayWalletRechargePackageTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayWalletRechargePackageTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayWalletRechargePackageTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayWalletRechargePackageDO().apply {
        id = row[PayWalletRechargePackageTable.id]
        name = row[PayWalletRechargePackageTable.name]
        payPrice = row[PayWalletRechargePackageTable.payPrice]
        bonusPrice = row[PayWalletRechargePackageTable.bonusPrice]
        status = row[PayWalletRechargePackageTable.status]
        creator = row[PayWalletRechargePackageTable.creator]
        createTime = row[PayWalletRechargePackageTable.createTime]
        updater = row[PayWalletRechargePackageTable.updater]
        updateTime = row[PayWalletRechargePackageTable.updateTime]
        deleted = row[PayWalletRechargePackageTable.deleted]
        tenantId = row[PayWalletRechargePackageTable.tenantId]
    }
}
