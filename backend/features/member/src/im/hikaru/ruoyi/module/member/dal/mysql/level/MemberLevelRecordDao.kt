package im.hikaru.ruoyi.module.member.dal.mysql.level

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.record.MemberLevelRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelRecordDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberLevelRecordDao {
    fun selectById(id: Long): MemberLevelRecordDO? = transaction { MemberLevelRecordTable.selectAll().where { conditions(MemberLevelRecordTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberLevelRecordDO> = if (ids.isEmpty()) emptyList() else transaction { MemberLevelRecordTable.selectAll().where { conditions(MemberLevelRecordTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberLevelRecordDO> = transaction { MemberLevelRecordTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberLevelRecordTable.selectAll().where { conditions() }.count() }
    fun selectPage(reqVO: MemberLevelRecordPageReqVO): PageResult<MemberLevelRecordDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.userId?.let { ops += MemberLevelRecordTable.userId eq it }
        reqVO.levelId?.let { ops += MemberLevelRecordTable.levelId eq it }
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MemberLevelRecordTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberLevelRecordTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        MemberLevelRecordTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberLevelRecordTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }
    fun insert(entity: MemberLevelRecordDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberLevelRecordTable.insert {
            it[MemberLevelRecordTable.userId] = entity.userId
            it[MemberLevelRecordTable.levelId] = entity.levelId
            it[MemberLevelRecordTable.level] = entity.level
            it[MemberLevelRecordTable.discountPercent] = entity.discountPercent
            it[MemberLevelRecordTable.experience] = entity.experience
            it[MemberLevelRecordTable.userExperience] = entity.userExperience
            it[MemberLevelRecordTable.remark] = entity.remark
            it[MemberLevelRecordTable.description] = entity.description
            it[MemberLevelRecordTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberLevelRecordTable.creator] = entity.creator
            it[MemberLevelRecordTable.updater] = entity.updater
            it[MemberLevelRecordTable.createTime] = requireNotNull(entity.createTime)
            it[MemberLevelRecordTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberLevelRecordTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberLevelRecordDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberLevelRecordTable.update(where = { conditions(MemberLevelRecordTable.id eq id) }) {
            entity.userId?.let { value -> it[MemberLevelRecordTable.userId] = value }
            entity.levelId?.let { value -> it[MemberLevelRecordTable.levelId] = value }
            entity.level?.let { value -> it[MemberLevelRecordTable.level] = value }
            entity.discountPercent?.let { value -> it[MemberLevelRecordTable.discountPercent] = value }
            entity.experience?.let { value -> it[MemberLevelRecordTable.experience] = value }
            entity.userExperience?.let { value -> it[MemberLevelRecordTable.userExperience] = value }
            entity.remark?.let { value -> it[MemberLevelRecordTable.remark] = value }
            entity.description?.let { value -> it[MemberLevelRecordTable.description] = value }
            entity.updater?.let { value -> it[MemberLevelRecordTable.updater] = value }
            it[MemberLevelRecordTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberLevelRecordTable.update(where = { conditions(MemberLevelRecordTable.id eq id) }) { it[MemberLevelRecordTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberLevelRecordTable.update(where = { conditions(MemberLevelRecordTable.id inList ids) }) { it[MemberLevelRecordTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberLevelRecordTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberLevelRecordTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberLevelRecordDO().apply {
        id = row[MemberLevelRecordTable.id]
        userId = row[MemberLevelRecordTable.userId]
        levelId = row[MemberLevelRecordTable.levelId]
        level = row[MemberLevelRecordTable.level]
        discountPercent = row[MemberLevelRecordTable.discountPercent]
        experience = row[MemberLevelRecordTable.experience]
        userExperience = row[MemberLevelRecordTable.userExperience]
        remark = row[MemberLevelRecordTable.remark]
        description = row[MemberLevelRecordTable.description]
        creator = row[MemberLevelRecordTable.creator]
        createTime = row[MemberLevelRecordTable.createTime]
        updater = row[MemberLevelRecordTable.updater]
        updateTime = row[MemberLevelRecordTable.updateTime]
        deleted = row[MemberLevelRecordTable.deleted]
        tenantId = row[MemberLevelRecordTable.tenantId]
    }
}
