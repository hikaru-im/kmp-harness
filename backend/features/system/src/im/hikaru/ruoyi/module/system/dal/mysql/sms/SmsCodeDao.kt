package im.hikaru.ruoyi.module.system.dal.mysql.sms

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsCodeDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object SmsCodeDao {
    fun selectById(id: Long): SmsCodeDO? = transaction {
        SmsCodeTable.selectAll().where { conditions(SmsCodeTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectLastByMobile(mobile: String, code: String? = null, scene: Int? = null): SmsCodeDO? = transaction {
        val ops = mutableListOf<Op<Boolean>>(SmsCodeTable.mobile eq mobile)
        code?.let { ops += SmsCodeTable.code eq it }
        scene?.let { ops += SmsCodeTable.scene eq it }
        SmsCodeTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(SmsCodeTable.id, SortOrder.DESC).limit(1).singleOrNull()?.let(::toEntity)
    }

    fun insert(entity: SmsCodeDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            SmsCodeTable.insert {
                it[mobile] = requireNotNull(entity.mobile)
                it[code] = requireNotNull(entity.code)
                it[createIp] = requireNotNull(entity.createIp)
                it[scene] = requireNotNull(entity.scene)
                it[todayIndex] = requireNotNull(entity.todayIndex)
                it[used] = entity.used ?: false
                it[usedTime] = entity.usedTime
                it[usedIp] = entity.usedIp
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(SmsCodeTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: SmsCodeDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            SmsCodeTable.update(where = { conditions(SmsCodeTable.id eq requireNotNull(entity.id)) }) {
                entity.used?.let { value -> it[used] = value }
                it[usedTime] = entity.usedTime
                it[usedIp] = entity.usedIp
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(SmsCodeTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += SmsCodeTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = SmsCodeDO().apply {
        id = row[SmsCodeTable.id]
        mobile = row[SmsCodeTable.mobile]
        code = row[SmsCodeTable.code]
        createIp = row[SmsCodeTable.createIp]
        scene = row[SmsCodeTable.scene]
        todayIndex = row[SmsCodeTable.todayIndex]
        used = row[SmsCodeTable.used]
        usedTime = row[SmsCodeTable.usedTime]
        usedIp = row[SmsCodeTable.usedIp]
        tenantId = row[SmsCodeTable.tenantId]
        creator = row[SmsCodeTable.creator]
        createTime = row[SmsCodeTable.createTime]
        updater = row[SmsCodeTable.updater]
        updateTime = row[SmsCodeTable.updateTime]
        deleted = row[SmsCodeTable.deleted]
    }
}
