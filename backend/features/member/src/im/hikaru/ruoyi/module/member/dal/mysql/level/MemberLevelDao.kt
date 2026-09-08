package im.hikaru.ruoyi.module.member.dal.mysql.level

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelListReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberLevelDao {
    fun selectById(id: Long): MemberLevelDO? = transaction { MemberLevelTable.selectAll().where { conditions(MemberLevelTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberLevelDO> = if (ids.isEmpty()) emptyList() else transaction { MemberLevelTable.selectAll().where { conditions(MemberLevelTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberLevelDO> = transaction { MemberLevelTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberLevelTable.selectAll().where { conditions() }.count() }
    fun selectList(reqVO: MemberLevelListReqVO): List<MemberLevelDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.name?.takeIf { it.isNotBlank() }?.let { ops += MemberLevelTable.name like "%$it%" }
        reqVO.status?.let { ops += MemberLevelTable.status eq it }
        MemberLevelTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberLevelTable.level, SortOrder.ASC)
            .map(::toEntity)
    }
    fun selectListByStatus(status: Int): List<MemberLevelDO> = transaction {
        MemberLevelTable.selectAll().where { conditions(MemberLevelTable.status eq status) }
            .orderBy(MemberLevelTable.level, SortOrder.ASC)
            .map(::toEntity)
    }
    fun insert(entity: MemberLevelDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberLevelTable.insert {
            it[MemberLevelTable.name] = entity.name
            it[MemberLevelTable.level] = entity.level
            it[MemberLevelTable.experience] = entity.experience
            it[MemberLevelTable.discountPercent] = entity.discountPercent
            it[MemberLevelTable.icon] = entity.icon
            it[MemberLevelTable.backgroundUrl] = entity.backgroundUrl
            it[MemberLevelTable.status] = entity.status
            it[MemberLevelTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberLevelTable.creator] = entity.creator
            it[MemberLevelTable.updater] = entity.updater
            it[MemberLevelTable.createTime] = requireNotNull(entity.createTime)
            it[MemberLevelTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberLevelTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberLevelDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberLevelTable.update(where = { conditions(MemberLevelTable.id eq id) }) {
            entity.name?.let { value -> it[MemberLevelTable.name] = value }
            entity.level?.let { value -> it[MemberLevelTable.level] = value }
            entity.experience?.let { value -> it[MemberLevelTable.experience] = value }
            entity.discountPercent?.let { value -> it[MemberLevelTable.discountPercent] = value }
            entity.icon?.let { value -> it[MemberLevelTable.icon] = value }
            entity.backgroundUrl?.let { value -> it[MemberLevelTable.backgroundUrl] = value }
            entity.status?.let { value -> it[MemberLevelTable.status] = value }
            entity.updater?.let { value -> it[MemberLevelTable.updater] = value }
            it[MemberLevelTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberLevelTable.update(where = { conditions(MemberLevelTable.id eq id) }) { it[MemberLevelTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberLevelTable.update(where = { conditions(MemberLevelTable.id inList ids) }) { it[MemberLevelTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberLevelTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberLevelTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberLevelDO().apply {
        id = row[MemberLevelTable.id]
        name = row[MemberLevelTable.name]
        level = row[MemberLevelTable.level]
        experience = row[MemberLevelTable.experience]
        discountPercent = row[MemberLevelTable.discountPercent]
        icon = row[MemberLevelTable.icon]
        backgroundUrl = row[MemberLevelTable.backgroundUrl]
        status = row[MemberLevelTable.status]
        creator = row[MemberLevelTable.creator]
        createTime = row[MemberLevelTable.createTime]
        updater = row[MemberLevelTable.updater]
        updateTime = row[MemberLevelTable.updateTime]
        deleted = row[MemberLevelTable.deleted]
        tenantId = row[MemberLevelTable.tenantId]
    }
}
