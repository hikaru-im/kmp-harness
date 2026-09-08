package im.hikaru.ruoyi.module.pay.dal.mysql.channel

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayChannelDao {
    fun selectById(id: Long): PayChannelDO? = transaction { PayChannelTable.selectAll().where { conditions(PayChannelTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PayChannelDO> = if (ids.isEmpty()) emptyList() else transaction { PayChannelTable.selectAll().where { conditions(PayChannelTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayChannelDO> = transaction { PayChannelTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectByAppIdAndCode(appId: Long, code: String): PayChannelDO? = transaction {
        PayChannelTable.selectAll().where { conditions(PayChannelTable.appId eq appId, PayChannelTable.code eq code) }
            .singleOrNull()?.let(::toEntity)
    }
    fun selectListByAppIds(appIds: Collection<Long>): List<PayChannelDO> = if (appIds.isEmpty()) emptyList() else transaction {
        PayChannelTable.selectAll().where { conditions(PayChannelTable.appId inList appIds) }.map(::toEntity)
    }
    fun selectListByAppId(appId: Long, status: Int? = null): List<PayChannelDO> = transaction {
        val extra = if (status == null) arrayOf(PayChannelTable.appId eq appId) else arrayOf(PayChannelTable.appId eq appId, PayChannelTable.status eq status)
        PayChannelTable.selectAll().where { conditions(*extra) }.map(::toEntity)
    }
    fun selectCount(): Long = transaction { PayChannelTable.selectAll().where { conditions() }.count() }
    fun insert(entity: PayChannelDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayChannelTable.insert {
            it[PayChannelTable.code] = entity.code
            it[PayChannelTable.status] = entity.status
            it[PayChannelTable.feeRate] = entity.feeRate
            it[PayChannelTable.remark] = entity.remark
            it[PayChannelTable.appId] = entity.appId
            it[PayChannelTable.config] = entity.config
            it[PayChannelTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayChannelTable.creator] = entity.creator
            it[PayChannelTable.updater] = entity.updater
            it[PayChannelTable.createTime] = requireNotNull(entity.createTime)
            it[PayChannelTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayChannelTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayChannelDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayChannelTable.update(where = { conditions(PayChannelTable.id eq id) }) {
            entity.code?.let { value -> it[PayChannelTable.code] = value }
            entity.status?.let { value -> it[PayChannelTable.status] = value }
            entity.feeRate?.let { value -> it[PayChannelTable.feeRate] = value }
            entity.remark?.let { value -> it[PayChannelTable.remark] = value }
            entity.appId?.let { value -> it[PayChannelTable.appId] = value }
            entity.config?.let { value -> it[PayChannelTable.config] = value }
            entity.updater?.let { value -> it[PayChannelTable.updater] = value }
            it[PayChannelTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayChannelTable.update(where = { conditions(PayChannelTable.id eq id) }) { it[PayChannelTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayChannelTable.update(where = { conditions(PayChannelTable.id inList ids) }) { it[PayChannelTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayChannelTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayChannelTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayChannelDO().apply {
        id = row[PayChannelTable.id]
        code = row[PayChannelTable.code]
        status = row[PayChannelTable.status]
        feeRate = row[PayChannelTable.feeRate]
        remark = row[PayChannelTable.remark]
        appId = row[PayChannelTable.appId]
        config = row[PayChannelTable.config]
        creator = row[PayChannelTable.creator]
        createTime = row[PayChannelTable.createTime]
        updater = row[PayChannelTable.updater]
        updateTime = row[PayChannelTable.updateTime]
        deleted = row[PayChannelTable.deleted]
        tenantId = row[PayChannelTable.tenantId]
    }
}
