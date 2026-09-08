package im.hikaru.ruoyi.module.pay.dal.mysql.app

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppPageReqVO
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayAppDao {
    fun selectById(id: Long): PayAppDO? = transaction { PayAppTable.selectAll().where { conditions(PayAppTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByAppKey(appKey: String): PayAppDO? = transaction { PayAppTable.selectAll().where { conditions(PayAppTable.appKey eq appKey) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayAppDO> = if (ids.isEmpty()) emptyList() else transaction { PayAppTable.selectAll().where { conditions(PayAppTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayAppDO> = transaction { PayAppTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayAppTable.selectAll().where { conditions() }.count() }
    fun selectPage(req: PayAppPageReqVO): PageResult<PayAppDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.name?.takeIf(String::isNotBlank)?.let { ops += PayAppTable.name like "%$it%" }
        req.appKey?.takeIf(String::isNotBlank)?.let { ops += PayAppTable.appKey like "%$it%" }
        req.status?.let { ops += PayAppTable.status eq it }
        req.createTime?.getOrNull(0)?.let { ops += PayAppTable.createTime greaterEq it.toKotlinLocalDateTime() }
        req.createTime?.getOrNull(1)?.let { ops += PayAppTable.createTime lessEq it.toKotlinLocalDateTime() }
        val all = PayAppTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(PayAppTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong()
        if (req.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((req.pageNo - 1) * req.pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + req.pageSize, all.size)))
    }
    fun insert(entity: PayAppDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayAppTable.insert {
            it[PayAppTable.appKey] = entity.appKey
            it[PayAppTable.name] = entity.name
            it[PayAppTable.status] = entity.status
            it[PayAppTable.remark] = entity.remark
            it[PayAppTable.orderNotifyUrl] = entity.orderNotifyUrl
            it[PayAppTable.refundNotifyUrl] = entity.refundNotifyUrl
            it[PayAppTable.transferNotifyUrl] = entity.transferNotifyUrl
            it[PayAppTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayAppTable.creator] = entity.creator
            it[PayAppTable.updater] = entity.updater
            it[PayAppTable.createTime] = requireNotNull(entity.createTime)
            it[PayAppTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayAppTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayAppDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayAppTable.update(where = { conditions(PayAppTable.id eq id) }) {
            entity.appKey?.let { value -> it[PayAppTable.appKey] = value }
            entity.name?.let { value -> it[PayAppTable.name] = value }
            entity.status?.let { value -> it[PayAppTable.status] = value }
            entity.remark?.let { value -> it[PayAppTable.remark] = value }
            entity.orderNotifyUrl?.let { value -> it[PayAppTable.orderNotifyUrl] = value }
            entity.refundNotifyUrl?.let { value -> it[PayAppTable.refundNotifyUrl] = value }
            entity.transferNotifyUrl?.let { value -> it[PayAppTable.transferNotifyUrl] = value }
            entity.updater?.let { value -> it[PayAppTable.updater] = value }
            it[PayAppTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayAppTable.update(where = { conditions(PayAppTable.id eq id) }) { it[PayAppTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayAppTable.update(where = { conditions(PayAppTable.id inList ids) }) { it[PayAppTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayAppTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayAppTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayAppDO().apply {
        id = row[PayAppTable.id]
        appKey = row[PayAppTable.appKey]
        name = row[PayAppTable.name]
        status = row[PayAppTable.status]
        remark = row[PayAppTable.remark]
        orderNotifyUrl = row[PayAppTable.orderNotifyUrl]
        refundNotifyUrl = row[PayAppTable.refundNotifyUrl]
        transferNotifyUrl = row[PayAppTable.transferNotifyUrl]
        creator = row[PayAppTable.creator]
        createTime = row[PayAppTable.createTime]
        updater = row[PayAppTable.updater]
        updateTime = row[PayAppTable.updateTime]
        deleted = row[PayAppTable.deleted]
        tenantId = row[PayAppTable.tenantId]
    }
}
