package im.hikaru.ruoyi.module.member.dal.mysql.address

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.dal.dataobject.address.MemberAddressDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberAddressDao {
    fun selectById(id: Long): MemberAddressDO? = transaction { MemberAddressTable.selectAll().where { conditions(MemberAddressTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberAddressDO> = if (ids.isEmpty()) emptyList() else transaction { MemberAddressTable.selectAll().where { conditions(MemberAddressTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberAddressDO> = transaction { MemberAddressTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberAddressTable.selectAll().where { conditions() }.count() }
    fun selectByIdAndUserId(id: Long, userId: Long): MemberAddressDO? = transaction {
        MemberAddressTable.selectAll()
            .where { conditions(MemberAddressTable.id eq id, MemberAddressTable.userId eq userId) }
            .singleOrNull()
            ?.let(::toEntity)
    }
    fun selectListByUserIdAndDefaultStatus(userId: Long, defaultStatus: Boolean?): List<MemberAddressDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(MemberAddressTable.userId eq userId)
        defaultStatus?.let { ops += MemberAddressTable.defaultStatus eq it }
        MemberAddressTable.selectAll().where { conditions(*ops.toTypedArray()) }.map(::toEntity)
    }
    fun insert(entity: MemberAddressDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberAddressTable.insert {
            it[MemberAddressTable.userId] = entity.userId
            it[MemberAddressTable.name] = entity.name
            it[MemberAddressTable.mobile] = entity.mobile
            it[MemberAddressTable.areaId] = entity.areaId
            it[MemberAddressTable.detailAddress] = entity.detailAddress
            it[MemberAddressTable.defaultStatus] = entity.defaultStatus
            it[MemberAddressTable.version] = entity.version
            it[MemberAddressTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberAddressTable.creator] = entity.creator
            it[MemberAddressTable.updater] = entity.updater
            it[MemberAddressTable.createTime] = requireNotNull(entity.createTime)
            it[MemberAddressTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberAddressTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberAddressDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberAddressTable.update(where = { conditions(MemberAddressTable.id eq id) }) {
            entity.userId?.let { value -> it[MemberAddressTable.userId] = value }
            entity.name?.let { value -> it[MemberAddressTable.name] = value }
            entity.mobile?.let { value -> it[MemberAddressTable.mobile] = value }
            entity.areaId?.let { value -> it[MemberAddressTable.areaId] = value }
            entity.detailAddress?.let { value -> it[MemberAddressTable.detailAddress] = value }
            entity.defaultStatus?.let { value -> it[MemberAddressTable.defaultStatus] = value }
            it[MemberAddressTable.version] = entity.version
            entity.updater?.let { value -> it[MemberAddressTable.updater] = value }
            it[MemberAddressTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun updateByIdAndVersion(userId: Long, entity: MemberAddressDO, expectedVersion: Long): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberAddressTable.update(where = {
            conditions(
                MemberAddressTable.id eq id,
                MemberAddressTable.userId eq userId,
                MemberAddressTable.version eq expectedVersion,
            )
        }) {
            entity.name?.let { value -> it[MemberAddressTable.name] = value }
            entity.mobile?.let { value -> it[MemberAddressTable.mobile] = value }
            entity.areaId?.let { value -> it[MemberAddressTable.areaId] = value }
            entity.detailAddress?.let { value -> it[MemberAddressTable.detailAddress] = value }
            entity.defaultStatus?.let { value -> it[MemberAddressTable.defaultStatus] = value }
            entity.updater?.let { value -> it[MemberAddressTable.updater] = value }
            it[MemberAddressTable.version] = expectedVersion + 1
            it[MemberAddressTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteByIdAndVersion(userId: Long, id: Long, expectedVersion: Long): Int {
        val entity = MemberAddressDO().apply { this.id = id }
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { MemberAddressTable.update(where = {
            conditions(
                MemberAddressTable.id eq id,
                MemberAddressTable.userId eq userId,
                MemberAddressTable.version eq expectedVersion,
            )
        }) {
            it[MemberAddressTable.deleted] = true
            it[MemberAddressTable.version] = expectedVersion + 1
            entity.updater?.let { value -> it[MemberAddressTable.updater] = value }
            it[MemberAddressTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberAddressTable.update(where = { conditions(MemberAddressTable.id eq id) }) { it[MemberAddressTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberAddressTable.update(where = { conditions(MemberAddressTable.id inList ids) }) { it[MemberAddressTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberAddressTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberAddressTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberAddressDO().apply {
        id = row[MemberAddressTable.id]
        userId = row[MemberAddressTable.userId]
        name = row[MemberAddressTable.name]
        mobile = row[MemberAddressTable.mobile]
        areaId = row[MemberAddressTable.areaId]
        detailAddress = row[MemberAddressTable.detailAddress]
        defaultStatus = row[MemberAddressTable.defaultStatus]
        version = row[MemberAddressTable.version]
        creator = row[MemberAddressTable.creator]
        createTime = row[MemberAddressTable.createTime]
        updater = row[MemberAddressTable.updater]
        updateTime = row[MemberAddressTable.updateTime]
        deleted = row[MemberAddressTable.deleted]
        tenantId = row[MemberAddressTable.tenantId]
    }
}
