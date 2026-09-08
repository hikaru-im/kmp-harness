package im.hikaru.ruoyi.module.member.dal.mysql.signin

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInConfigDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberSignInConfigDao {
    fun selectById(id: Long): MemberSignInConfigDO? = transaction { MemberSignInConfigTable.selectAll().where { conditions(MemberSignInConfigTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberSignInConfigDO> = if (ids.isEmpty()) emptyList() else transaction { MemberSignInConfigTable.selectAll().where { conditions(MemberSignInConfigTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberSignInConfigDO> = transaction { MemberSignInConfigTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberSignInConfigTable.selectAll().where { conditions() }.count() }
    fun selectByDay(day: Int): MemberSignInConfigDO? = transaction {
        MemberSignInConfigTable.selectAll().where { conditions(MemberSignInConfigTable.day eq day) }.singleOrNull()?.let(::toEntity)
    }
    fun selectListByStatus(status: Int): List<MemberSignInConfigDO> = transaction {
        MemberSignInConfigTable.selectAll().where { conditions(MemberSignInConfigTable.status eq status) }.map(::toEntity)
    }
    fun insert(entity: MemberSignInConfigDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberSignInConfigTable.insert {
            it[MemberSignInConfigTable.day] = entity.day
            it[MemberSignInConfigTable.point] = entity.point
            it[MemberSignInConfigTable.experience] = entity.experience
            it[MemberSignInConfigTable.status] = entity.status
            it[MemberSignInConfigTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberSignInConfigTable.creator] = entity.creator
            it[MemberSignInConfigTable.updater] = entity.updater
            it[MemberSignInConfigTable.createTime] = requireNotNull(entity.createTime)
            it[MemberSignInConfigTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberSignInConfigTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberSignInConfigDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberSignInConfigTable.update(where = { conditions(MemberSignInConfigTable.id eq id) }) {
            entity.day?.let { value -> it[MemberSignInConfigTable.day] = value }
            entity.point?.let { value -> it[MemberSignInConfigTable.point] = value }
            entity.experience?.let { value -> it[MemberSignInConfigTable.experience] = value }
            entity.status?.let { value -> it[MemberSignInConfigTable.status] = value }
            entity.updater?.let { value -> it[MemberSignInConfigTable.updater] = value }
            it[MemberSignInConfigTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberSignInConfigTable.update(where = { conditions(MemberSignInConfigTable.id eq id) }) { it[MemberSignInConfigTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberSignInConfigTable.update(where = { conditions(MemberSignInConfigTable.id inList ids) }) { it[MemberSignInConfigTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberSignInConfigTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberSignInConfigTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberSignInConfigDO().apply {
        id = row[MemberSignInConfigTable.id]
        day = row[MemberSignInConfigTable.day]
        point = row[MemberSignInConfigTable.point]
        experience = row[MemberSignInConfigTable.experience]
        status = row[MemberSignInConfigTable.status]
        creator = row[MemberSignInConfigTable.creator]
        createTime = row[MemberSignInConfigTable.createTime]
        updater = row[MemberSignInConfigTable.updater]
        updateTime = row[MemberSignInConfigTable.updateTime]
        deleted = row[MemberSignInConfigTable.deleted]
        tenantId = row[MemberSignInConfigTable.tenantId]
    }
}
