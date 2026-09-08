package im.hikaru.ruoyi.module.pay.dal.mysql.demo

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.PayDemoWithdrawDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayDemoWithdrawDao {
    fun selectById(id: Long): PayDemoWithdrawDO? = transaction { PayDemoWithdrawTable.selectAll().where { conditions(PayDemoWithdrawTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayDemoWithdrawDO> = if (ids.isEmpty()) emptyList() else transaction { PayDemoWithdrawTable.selectAll().where { conditions(PayDemoWithdrawTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayDemoWithdrawDO> = transaction { PayDemoWithdrawTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayDemoWithdrawTable.selectAll().where { conditions() }.count() }
    fun selectPage(page: PageParam): PageResult<PayDemoWithdrawDO> = transaction {
        val all = PayDemoWithdrawTable.selectAll().where { conditions() }.orderBy(PayDemoWithdrawTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong(); if (page.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + page.pageSize, all.size)))
    }
    fun updateByIdAndStatus(id: Long, expected: Int, entity: PayDemoWithdrawDO): Int {
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayDemoWithdrawTable.update(where = {
            conditions(PayDemoWithdrawTable.id eq id, PayDemoWithdrawTable.status eq expected)
        }) {
            entity.status?.let { value -> it[PayDemoWithdrawTable.status] = value }
            entity.payTransferId?.let { value -> it[PayDemoWithdrawTable.payTransferId] = value }
            entity.transferChannelCode?.let { value -> it[PayDemoWithdrawTable.transferChannelCode] = value }
            entity.transferTime?.let { value -> it[PayDemoWithdrawTable.transferTime] = value }
            entity.transferErrorMsg?.let { value -> it[PayDemoWithdrawTable.transferErrorMsg] = value }
            entity.updater?.let { value -> it[PayDemoWithdrawTable.updater] = value }
            it[PayDemoWithdrawTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun insert(entity: PayDemoWithdrawDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayDemoWithdrawTable.insert {
            it[PayDemoWithdrawTable.subject] = entity.subject
            it[PayDemoWithdrawTable.price] = entity.price
            it[PayDemoWithdrawTable.userAccount] = entity.userAccount
            it[PayDemoWithdrawTable.userName] = entity.userName
            it[PayDemoWithdrawTable.type] = entity.type
            it[PayDemoWithdrawTable.status] = entity.status
            it[PayDemoWithdrawTable.payTransferId] = entity.payTransferId
            it[PayDemoWithdrawTable.transferChannelCode] = entity.transferChannelCode
            it[PayDemoWithdrawTable.transferTime] = entity.transferTime
            it[PayDemoWithdrawTable.transferErrorMsg] = entity.transferErrorMsg
            it[PayDemoWithdrawTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayDemoWithdrawTable.creator] = entity.creator
            it[PayDemoWithdrawTable.updater] = entity.updater
            it[PayDemoWithdrawTable.createTime] = requireNotNull(entity.createTime)
            it[PayDemoWithdrawTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayDemoWithdrawTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayDemoWithdrawDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayDemoWithdrawTable.update(where = { conditions(PayDemoWithdrawTable.id eq id) }) {
            entity.subject?.let { value -> it[PayDemoWithdrawTable.subject] = value }
            entity.price?.let { value -> it[PayDemoWithdrawTable.price] = value }
            entity.userAccount?.let { value -> it[PayDemoWithdrawTable.userAccount] = value }
            entity.userName?.let { value -> it[PayDemoWithdrawTable.userName] = value }
            entity.type?.let { value -> it[PayDemoWithdrawTable.type] = value }
            entity.status?.let { value -> it[PayDemoWithdrawTable.status] = value }
            entity.payTransferId?.let { value -> it[PayDemoWithdrawTable.payTransferId] = value }
            entity.transferChannelCode?.let { value -> it[PayDemoWithdrawTable.transferChannelCode] = value }
            entity.transferTime?.let { value -> it[PayDemoWithdrawTable.transferTime] = value }
            entity.transferErrorMsg?.let { value -> it[PayDemoWithdrawTable.transferErrorMsg] = value }
            entity.updater?.let { value -> it[PayDemoWithdrawTable.updater] = value }
            it[PayDemoWithdrawTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayDemoWithdrawTable.update(where = { conditions(PayDemoWithdrawTable.id eq id) }) { it[PayDemoWithdrawTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayDemoWithdrawTable.update(where = { conditions(PayDemoWithdrawTable.id inList ids) }) { it[PayDemoWithdrawTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayDemoWithdrawTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayDemoWithdrawTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayDemoWithdrawDO().apply {
        id = row[PayDemoWithdrawTable.id]
        subject = row[PayDemoWithdrawTable.subject]
        price = row[PayDemoWithdrawTable.price]
        userAccount = row[PayDemoWithdrawTable.userAccount]
        userName = row[PayDemoWithdrawTable.userName]
        type = row[PayDemoWithdrawTable.type]
        status = row[PayDemoWithdrawTable.status]
        payTransferId = row[PayDemoWithdrawTable.payTransferId]
        transferChannelCode = row[PayDemoWithdrawTable.transferChannelCode]
        transferTime = row[PayDemoWithdrawTable.transferTime]
        transferErrorMsg = row[PayDemoWithdrawTable.transferErrorMsg]
        creator = row[PayDemoWithdrawTable.creator]
        createTime = row[PayDemoWithdrawTable.createTime]
        updater = row[PayDemoWithdrawTable.updater]
        updateTime = row[PayDemoWithdrawTable.updateTime]
        deleted = row[PayDemoWithdrawTable.deleted]
        tenantId = row[PayDemoWithdrawTable.tenantId]
    }
}
