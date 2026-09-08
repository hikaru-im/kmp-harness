package im.hikaru.ruoyi.module.member.dal.mysql.tag

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.tag.MemberTagDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberTagDao {
    fun selectById(id: Long): MemberTagDO? = transaction { MemberTagTable.selectAll().where { conditions(MemberTagTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberTagDO> = if (ids.isEmpty()) emptyList() else transaction { MemberTagTable.selectAll().where { conditions(MemberTagTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberTagDO> = transaction { MemberTagTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberTagTable.selectAll().where { conditions() }.count() }
    fun selectByName(name: String): MemberTagDO? = transaction {
        MemberTagTable.selectAll().where { conditions(MemberTagTable.name eq name) }.singleOrNull()?.let(::toEntity)
    }
    fun selectPage(reqVO: MemberTagPageReqVO): PageResult<MemberTagDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.name?.takeIf { it.isNotBlank() }?.let { ops += MemberTagTable.name like "%$it%" }
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MemberTagTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberTagTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        MemberTagTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberTagTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }
    fun insert(entity: MemberTagDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberTagTable.insert {
            it[MemberTagTable.name] = entity.name
            it[MemberTagTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberTagTable.creator] = entity.creator
            it[MemberTagTable.updater] = entity.updater
            it[MemberTagTable.createTime] = requireNotNull(entity.createTime)
            it[MemberTagTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberTagTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberTagDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberTagTable.update(where = { conditions(MemberTagTable.id eq id) }) {
            entity.name?.let { value -> it[MemberTagTable.name] = value }
            entity.updater?.let { value -> it[MemberTagTable.updater] = value }
            it[MemberTagTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberTagTable.update(where = { conditions(MemberTagTable.id eq id) }) { it[MemberTagTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberTagTable.update(where = { conditions(MemberTagTable.id inList ids) }) { it[MemberTagTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberTagTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberTagTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberTagDO().apply {
        id = row[MemberTagTable.id]
        name = row[MemberTagTable.name]
        creator = row[MemberTagTable.creator]
        createTime = row[MemberTagTable.createTime]
        updater = row[MemberTagTable.updater]
        updateTime = row[MemberTagTable.updateTime]
        deleted = row[MemberTagTable.deleted]
        tenantId = row[MemberTagTable.tenantId]
    }
}
