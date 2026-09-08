package im.hikaru.ruoyi.module.pay.dal.mysql.notify

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskPageReqVO
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyLogDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyTaskDO
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyStatusEnum
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime as JavaLocalDateTime

object PayNotifyTaskDao {
    fun selectById(id: Long): PayNotifyTaskDO? = transaction { PayNotifyTaskTable.selectAll().where { conditions(PayNotifyTaskTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByTypeAndDataId(type: Int, dataId: Long): PayNotifyTaskDO? = transaction { PayNotifyTaskTable.selectAll().where { conditions(PayNotifyTaskTable.type eq type, PayNotifyTaskTable.dataId eq dataId) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayNotifyTaskDO> = if (ids.isEmpty()) emptyList() else transaction { PayNotifyTaskTable.selectAll().where { conditions(PayNotifyTaskTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayNotifyTaskDO> = transaction { PayNotifyTaskTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayNotifyTaskTable.selectAll().where { conditions() }.count() }
    fun selectPage(req: PayNotifyTaskPageReqVO): PageResult<PayNotifyTaskDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.appId?.let { ops += PayNotifyTaskTable.appId eq it }
        req.type?.let { ops += PayNotifyTaskTable.type eq it }
        req.dataId?.let { ops += PayNotifyTaskTable.dataId eq it }
        req.status?.let { ops += PayNotifyTaskTable.status eq it }
        req.merchantOrderId?.takeIf(String::isNotBlank)?.let { ops += PayNotifyTaskTable.merchantOrderId like "%$it%" }
        req.merchantRefundId?.takeIf(String::isNotBlank)?.let { ops += PayNotifyTaskTable.merchantRefundId like "%$it%" }
        req.merchantTransferId?.takeIf(String::isNotBlank)?.let { ops += PayNotifyTaskTable.merchantTransferId like "%$it%" }
        req.createTime?.getOrNull(0)?.let { ops += PayNotifyTaskTable.createTime greaterEq it.toKotlinLocalDateTime() }
        req.createTime?.getOrNull(1)?.let { ops += PayNotifyTaskTable.createTime lessEq it.toKotlinLocalDateTime() }
        val all = PayNotifyTaskTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PayNotifyTaskTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong()
        if (req.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((req.pageNo - 1) * req.pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + req.pageSize, all.size)))
    }
    fun selectDue(limit: Int = 100): List<PayNotifyTaskDO> = transaction {
        val activeStatuses = listOf(
            PayNotifyStatusEnum.WAITING.status,
            PayNotifyStatusEnum.REQUEST_SUCCESS.status,
            PayNotifyStatusEnum.REQUEST_FAILURE.status,
        )
        PayNotifyTaskTable.selectAll().where {
            conditions(
                PayNotifyTaskTable.status inList activeStatuses,
                PayNotifyTaskTable.nextNotifyTime lessEq JavaLocalDateTime.now().toKotlinLocalDateTime(),
            )
        }.orderBy(PayNotifyTaskTable.nextNotifyTime, SortOrder.ASC)
            .orderBy(PayNotifyTaskTable.id, SortOrder.ASC)
            .limit(limit)
            .map(::toEntity)
    }
    fun insert(entity: PayNotifyTaskDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayNotifyTaskTable.insert {
            it[PayNotifyTaskTable.appId] = entity.appId
            it[PayNotifyTaskTable.type] = entity.type
            it[PayNotifyTaskTable.dataId] = entity.dataId
            it[PayNotifyTaskTable.merchantOrderId] = entity.merchantOrderId
            it[PayNotifyTaskTable.merchantRefundId] = entity.merchantRefundId
            it[PayNotifyTaskTable.merchantTransferId] = entity.merchantTransferId
            it[PayNotifyTaskTable.status] = entity.status
            it[PayNotifyTaskTable.nextNotifyTime] = entity.nextNotifyTime
            it[PayNotifyTaskTable.lastExecuteTime] = entity.lastExecuteTime
            it[PayNotifyTaskTable.notifyTimes] = entity.notifyTimes
            it[PayNotifyTaskTable.maxNotifyTimes] = entity.maxNotifyTimes
            it[PayNotifyTaskTable.notifyUrl] = entity.notifyUrl
            it[PayNotifyTaskTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayNotifyTaskTable.creator] = entity.creator
            it[PayNotifyTaskTable.updater] = entity.updater
            it[PayNotifyTaskTable.createTime] = requireNotNull(entity.createTime)
            it[PayNotifyTaskTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayNotifyTaskTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayNotifyTaskDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayNotifyTaskTable.update(where = { conditions(PayNotifyTaskTable.id eq id) }) {
            entity.appId?.let { value -> it[PayNotifyTaskTable.appId] = value }
            entity.type?.let { value -> it[PayNotifyTaskTable.type] = value }
            entity.dataId?.let { value -> it[PayNotifyTaskTable.dataId] = value }
            entity.merchantOrderId?.let { value -> it[PayNotifyTaskTable.merchantOrderId] = value }
            entity.merchantRefundId?.let { value -> it[PayNotifyTaskTable.merchantRefundId] = value }
            entity.merchantTransferId?.let { value -> it[PayNotifyTaskTable.merchantTransferId] = value }
            entity.status?.let { value -> it[PayNotifyTaskTable.status] = value }
            entity.nextNotifyTime?.let { value -> it[PayNotifyTaskTable.nextNotifyTime] = value }
            entity.lastExecuteTime?.let { value -> it[PayNotifyTaskTable.lastExecuteTime] = value }
            entity.notifyTimes?.let { value -> it[PayNotifyTaskTable.notifyTimes] = value }
            entity.maxNotifyTimes?.let { value -> it[PayNotifyTaskTable.maxNotifyTimes] = value }
            entity.notifyUrl?.let { value -> it[PayNotifyTaskTable.notifyUrl] = value }
            entity.updater?.let { value -> it[PayNotifyTaskTable.updater] = value }
            it[PayNotifyTaskTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun updateResultAndInsertLog(
        task: PayNotifyTaskDO,
        expectedNotifyTimes: Int,
        newStatus: Int,
        newNotifyTimes: Int,
        lastExecuteTime: LocalDateTime,
        nextNotifyTime: LocalDateTime?,
        response: String,
    ): Boolean = transaction {
        val update = PayNotifyTaskDO().apply { id = task.id }
        DefaultDBFieldHandler.fillOnUpdate(update)
        val updated = PayNotifyTaskTable.update(where = {
            conditions(
                PayNotifyTaskTable.id eq requireNotNull(task.id),
                PayNotifyTaskTable.notifyTimes eq expectedNotifyTimes,
            )
        }) {
            it[status] = newStatus
            it[notifyTimes] = newNotifyTimes
            it[PayNotifyTaskTable.lastExecuteTime] = lastExecuteTime
            it[PayNotifyTaskTable.nextNotifyTime] = nextNotifyTime
            it[updater] = update.updater
            it[updateTime] = requireNotNull(update.updateTime)
        }
        if (updated != 1) return@transaction false

        val log = PayNotifyLogDO().apply {
            taskId = task.id
            notifyTimes = newNotifyTimes
            status = newStatus
            this.response = response.take(MAX_RESPONSE_LENGTH)
            tenantId = task.tenantId
        }
        DefaultDBFieldHandler.fillOnInsert(log)
        log.id = PayNotifyLogTable.insert {
            it[taskId] = log.taskId
            it[notifyTimes] = log.notifyTimes
            it[PayNotifyLogTable.response] = log.response
            it[status] = log.status
            it[tenantId] = log.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[creator] = log.creator
            it[updater] = log.updater
            it[createTime] = requireNotNull(log.createTime)
            it[updateTime] = requireNotNull(log.updateTime)
        }.get(PayNotifyLogTable.id)
        true
    }
    fun deleteById(id: Long): Int = transaction { PayNotifyTaskTable.update(where = { conditions(PayNotifyTaskTable.id eq id) }) { it[PayNotifyTaskTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayNotifyTaskTable.update(where = { conditions(PayNotifyTaskTable.id inList ids) }) { it[PayNotifyTaskTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayNotifyTaskTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayNotifyTaskTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayNotifyTaskDO().apply {
        id = row[PayNotifyTaskTable.id]
        appId = row[PayNotifyTaskTable.appId]
        type = row[PayNotifyTaskTable.type]
        dataId = row[PayNotifyTaskTable.dataId]
        merchantOrderId = row[PayNotifyTaskTable.merchantOrderId]
        merchantRefundId = row[PayNotifyTaskTable.merchantRefundId]
        merchantTransferId = row[PayNotifyTaskTable.merchantTransferId]
        status = row[PayNotifyTaskTable.status]
        nextNotifyTime = row[PayNotifyTaskTable.nextNotifyTime]
        lastExecuteTime = row[PayNotifyTaskTable.lastExecuteTime]
        notifyTimes = row[PayNotifyTaskTable.notifyTimes]
        maxNotifyTimes = row[PayNotifyTaskTable.maxNotifyTimes]
        notifyUrl = row[PayNotifyTaskTable.notifyUrl]
        creator = row[PayNotifyTaskTable.creator]
        createTime = row[PayNotifyTaskTable.createTime]
        updater = row[PayNotifyTaskTable.updater]
        updateTime = row[PayNotifyTaskTable.updateTime]
        deleted = row[PayNotifyTaskTable.deleted]
        tenantId = row[PayNotifyTaskTable.tenantId]
    }

    private const val MAX_RESPONSE_LENGTH = 2048
}
