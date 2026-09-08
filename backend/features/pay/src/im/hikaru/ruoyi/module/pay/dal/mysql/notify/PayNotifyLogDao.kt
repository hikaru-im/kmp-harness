package im.hikaru.ruoyi.module.pay.dal.mysql.notify

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyLogDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayNotifyLogDao {
    fun selectById(id: Long): PayNotifyLogDO? = transaction { PayNotifyLogTable.selectAll().where { conditions(PayNotifyLogTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayNotifyLogDO> = if (ids.isEmpty()) emptyList() else transaction { PayNotifyLogTable.selectAll().where { conditions(PayNotifyLogTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayNotifyLogDO> = transaction { PayNotifyLogTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayNotifyLogTable.selectAll().where { conditions() }.count() }
    fun selectListByTaskId(taskId: Long): List<PayNotifyLogDO> = transaction { PayNotifyLogTable.selectAll().where { conditions(PayNotifyLogTable.taskId eq taskId) }.orderBy(PayNotifyLogTable.id).map(::toEntity) }
    fun insert(entity: PayNotifyLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayNotifyLogTable.insert {
            it[PayNotifyLogTable.taskId] = entity.taskId
            it[PayNotifyLogTable.notifyTimes] = entity.notifyTimes
            it[PayNotifyLogTable.response] = entity.response
            it[PayNotifyLogTable.status] = entity.status
            it[PayNotifyLogTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayNotifyLogTable.creator] = entity.creator
            it[PayNotifyLogTable.updater] = entity.updater
            it[PayNotifyLogTable.createTime] = requireNotNull(entity.createTime)
            it[PayNotifyLogTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayNotifyLogTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayNotifyLogDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayNotifyLogTable.update(where = { conditions(PayNotifyLogTable.id eq id) }) {
            entity.taskId?.let { value -> it[PayNotifyLogTable.taskId] = value }
            entity.notifyTimes?.let { value -> it[PayNotifyLogTable.notifyTimes] = value }
            entity.response?.let { value -> it[PayNotifyLogTable.response] = value }
            entity.status?.let { value -> it[PayNotifyLogTable.status] = value }
            entity.updater?.let { value -> it[PayNotifyLogTable.updater] = value }
            it[PayNotifyLogTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayNotifyLogTable.update(where = { conditions(PayNotifyLogTable.id eq id) }) { it[PayNotifyLogTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayNotifyLogTable.update(where = { conditions(PayNotifyLogTable.id inList ids) }) { it[PayNotifyLogTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayNotifyLogTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayNotifyLogTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayNotifyLogDO().apply {
        id = row[PayNotifyLogTable.id]
        taskId = row[PayNotifyLogTable.taskId]
        notifyTimes = row[PayNotifyLogTable.notifyTimes]
        response = row[PayNotifyLogTable.response]
        status = row[PayNotifyLogTable.status]
        creator = row[PayNotifyLogTable.creator]
        createTime = row[PayNotifyLogTable.createTime]
        updater = row[PayNotifyLogTable.updater]
        updateTime = row[PayNotifyLogTable.updateTime]
        deleted = row[PayNotifyLogTable.deleted]
        tenantId = row[PayNotifyLogTable.tenantId]
    }
}
