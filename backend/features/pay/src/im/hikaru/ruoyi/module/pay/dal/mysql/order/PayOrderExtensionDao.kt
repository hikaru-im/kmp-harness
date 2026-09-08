package im.hikaru.ruoyi.module.pay.dal.mysql.order

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderExtensionDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayOrderExtensionDao {
    fun selectById(id: Long): PayOrderExtensionDO? = transaction { PayOrderExtensionTable.selectAll().where { conditions(PayOrderExtensionTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByNo(no: String): PayOrderExtensionDO? = transaction { PayOrderExtensionTable.selectAll().where { conditions(PayOrderExtensionTable.no eq no) }.singleOrNull()?.let(::toEntity) }
    fun selectListByOrderId(orderId: Long): List<PayOrderExtensionDO> = transaction { PayOrderExtensionTable.selectAll().where { conditions(PayOrderExtensionTable.orderId eq orderId) }.map(::toEntity) }
    fun selectListByOrderIdAndStatus(orderId: Long, status: Int): List<PayOrderExtensionDO> = transaction {
        PayOrderExtensionTable.selectAll().where {
            conditions(PayOrderExtensionTable.orderId eq orderId, PayOrderExtensionTable.status eq status)
        }.map(::toEntity)
    }
    fun selectListByStatusAndCreateTimeGe(status: Int, minCreateTime: java.time.LocalDateTime): List<PayOrderExtensionDO> = transaction {
        PayOrderExtensionTable.selectAll().where {
            conditions(
                PayOrderExtensionTable.status eq status,
                PayOrderExtensionTable.createTime greaterEq minCreateTime.toKotlinLocalDateTime(),
            )
        }.map(::toEntity)
    }
    fun selectByIds(ids: Collection<Long>): List<PayOrderExtensionDO> = if (ids.isEmpty()) emptyList() else transaction { PayOrderExtensionTable.selectAll().where { conditions(PayOrderExtensionTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayOrderExtensionDO> = transaction { PayOrderExtensionTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayOrderExtensionTable.selectAll().where { conditions() }.count() }
    fun updateByIdAndStatus(id: Long, status: Int, entity: PayOrderExtensionDO): Int {
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayOrderExtensionTable.update(where = { conditions(PayOrderExtensionTable.id eq id, PayOrderExtensionTable.status eq status) }) {
            entity.status?.let { value -> it[PayOrderExtensionTable.status] = value }
            entity.channelErrorCode?.let { value -> it[PayOrderExtensionTable.channelErrorCode] = value }
            entity.channelErrorMsg?.let { value -> it[PayOrderExtensionTable.channelErrorMsg] = value }
            entity.channelNotifyData?.let { value -> it[PayOrderExtensionTable.channelNotifyData] = value }
            entity.channelExtras?.let { value -> it[PayOrderExtensionTable.channelExtras] = value }
            entity.updater?.let { value -> it[PayOrderExtensionTable.updater] = value }
            it[PayOrderExtensionTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun insert(entity: PayOrderExtensionDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayOrderExtensionTable.insert {
            it[PayOrderExtensionTable.no] = entity.no
            it[PayOrderExtensionTable.orderId] = entity.orderId
            it[PayOrderExtensionTable.channelId] = entity.channelId
            it[PayOrderExtensionTable.channelCode] = entity.channelCode
            it[PayOrderExtensionTable.userIp] = entity.userIp
            it[PayOrderExtensionTable.status] = entity.status
            it[PayOrderExtensionTable.channelExtras] = entity.channelExtras
            it[PayOrderExtensionTable.channelErrorCode] = entity.channelErrorCode
            it[PayOrderExtensionTable.channelErrorMsg] = entity.channelErrorMsg
            it[PayOrderExtensionTable.channelNotifyData] = entity.channelNotifyData
            it[PayOrderExtensionTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayOrderExtensionTable.creator] = entity.creator
            it[PayOrderExtensionTable.updater] = entity.updater
            it[PayOrderExtensionTable.createTime] = requireNotNull(entity.createTime)
            it[PayOrderExtensionTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayOrderExtensionTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayOrderExtensionDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayOrderExtensionTable.update(where = { conditions(PayOrderExtensionTable.id eq id) }) {
            entity.no?.let { value -> it[PayOrderExtensionTable.no] = value }
            entity.orderId?.let { value -> it[PayOrderExtensionTable.orderId] = value }
            entity.channelId?.let { value -> it[PayOrderExtensionTable.channelId] = value }
            entity.channelCode?.let { value -> it[PayOrderExtensionTable.channelCode] = value }
            entity.userIp?.let { value -> it[PayOrderExtensionTable.userIp] = value }
            entity.status?.let { value -> it[PayOrderExtensionTable.status] = value }
            entity.channelExtras?.let { value -> it[PayOrderExtensionTable.channelExtras] = value }
            entity.channelErrorCode?.let { value -> it[PayOrderExtensionTable.channelErrorCode] = value }
            entity.channelErrorMsg?.let { value -> it[PayOrderExtensionTable.channelErrorMsg] = value }
            entity.channelNotifyData?.let { value -> it[PayOrderExtensionTable.channelNotifyData] = value }
            entity.updater?.let { value -> it[PayOrderExtensionTable.updater] = value }
            it[PayOrderExtensionTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayOrderExtensionTable.update(where = { conditions(PayOrderExtensionTable.id eq id) }) { it[PayOrderExtensionTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayOrderExtensionTable.update(where = { conditions(PayOrderExtensionTable.id inList ids) }) { it[PayOrderExtensionTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayOrderExtensionTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayOrderExtensionTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayOrderExtensionDO().apply {
        id = row[PayOrderExtensionTable.id]
        no = row[PayOrderExtensionTable.no]
        orderId = row[PayOrderExtensionTable.orderId]
        channelId = row[PayOrderExtensionTable.channelId]
        channelCode = row[PayOrderExtensionTable.channelCode]
        userIp = row[PayOrderExtensionTable.userIp]
        status = row[PayOrderExtensionTable.status]
        channelExtras = row[PayOrderExtensionTable.channelExtras]
        channelErrorCode = row[PayOrderExtensionTable.channelErrorCode]
        channelErrorMsg = row[PayOrderExtensionTable.channelErrorMsg]
        channelNotifyData = row[PayOrderExtensionTable.channelNotifyData]
        creator = row[PayOrderExtensionTable.creator]
        createTime = row[PayOrderExtensionTable.createTime]
        updater = row[PayOrderExtensionTable.updater]
        updateTime = row[PayOrderExtensionTable.updateTime]
        deleted = row[PayOrderExtensionTable.deleted]
        tenantId = row[PayOrderExtensionTable.tenantId]
    }
}
