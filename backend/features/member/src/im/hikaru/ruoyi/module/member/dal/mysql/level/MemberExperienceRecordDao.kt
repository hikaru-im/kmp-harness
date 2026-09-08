package im.hikaru.ruoyi.module.member.dal.mysql.level

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience.MemberExperienceRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberExperienceRecordDO
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

object MemberExperienceRecordDao {
    fun selectById(id: Long): MemberExperienceRecordDO? = transaction { MemberExperienceRecordTable.selectAll().where { conditions(MemberExperienceRecordTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberExperienceRecordDO> = if (ids.isEmpty()) emptyList() else transaction { MemberExperienceRecordTable.selectAll().where { conditions(MemberExperienceRecordTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberExperienceRecordDO> = transaction { MemberExperienceRecordTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberExperienceRecordTable.selectAll().where { conditions() }.count() }
    fun selectPage(reqVO: MemberExperienceRecordPageReqVO): PageResult<MemberExperienceRecordDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.userId?.let { ops += MemberExperienceRecordTable.userId eq it }
        reqVO.bizId?.takeIf { it.isNotBlank() }?.let { ops += MemberExperienceRecordTable.bizId eq it }
        reqVO.bizType?.let { ops += MemberExperienceRecordTable.bizType eq it }
        reqVO.title?.takeIf { it.isNotBlank() }?.let { ops += MemberExperienceRecordTable.title eq it }
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MemberExperienceRecordTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberExperienceRecordTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        MemberExperienceRecordTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberExperienceRecordTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }
    fun selectPage(userId: Long, pageParam: PageParam): PageResult<MemberExperienceRecordDO> = transaction {
        MemberExperienceRecordTable.selectAll().where { conditions(MemberExperienceRecordTable.userId eq userId) }
            .orderBy(MemberExperienceRecordTable.id, SortOrder.DESC)
            .toPageResult(pageParam, ::toEntity)
    }
    fun insert(entity: MemberExperienceRecordDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberExperienceRecordTable.insert {
            it[MemberExperienceRecordTable.userId] = entity.userId
            it[MemberExperienceRecordTable.bizType] = entity.bizType
            it[MemberExperienceRecordTable.bizId] = entity.bizId
            it[MemberExperienceRecordTable.title] = entity.title
            it[MemberExperienceRecordTable.description] = entity.description
            it[MemberExperienceRecordTable.experience] = entity.experience
            it[MemberExperienceRecordTable.totalExperience] = entity.totalExperience
            it[MemberExperienceRecordTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberExperienceRecordTable.creator] = entity.creator
            it[MemberExperienceRecordTable.updater] = entity.updater
            it[MemberExperienceRecordTable.createTime] = requireNotNull(entity.createTime)
            it[MemberExperienceRecordTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberExperienceRecordTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberExperienceRecordDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberExperienceRecordTable.update(where = { conditions(MemberExperienceRecordTable.id eq id) }) {
            entity.userId?.let { value -> it[MemberExperienceRecordTable.userId] = value }
            entity.bizType?.let { value -> it[MemberExperienceRecordTable.bizType] = value }
            entity.bizId?.let { value -> it[MemberExperienceRecordTable.bizId] = value }
            entity.title?.let { value -> it[MemberExperienceRecordTable.title] = value }
            entity.description?.let { value -> it[MemberExperienceRecordTable.description] = value }
            entity.experience?.let { value -> it[MemberExperienceRecordTable.experience] = value }
            entity.totalExperience?.let { value -> it[MemberExperienceRecordTable.totalExperience] = value }
            entity.updater?.let { value -> it[MemberExperienceRecordTable.updater] = value }
            it[MemberExperienceRecordTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberExperienceRecordTable.update(where = { conditions(MemberExperienceRecordTable.id eq id) }) { it[MemberExperienceRecordTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberExperienceRecordTable.update(where = { conditions(MemberExperienceRecordTable.id inList ids) }) { it[MemberExperienceRecordTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberExperienceRecordTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberExperienceRecordTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberExperienceRecordDO().apply {
        id = row[MemberExperienceRecordTable.id]
        userId = row[MemberExperienceRecordTable.userId]
        bizType = row[MemberExperienceRecordTable.bizType]
        bizId = row[MemberExperienceRecordTable.bizId]
        title = row[MemberExperienceRecordTable.title]
        description = row[MemberExperienceRecordTable.description]
        experience = row[MemberExperienceRecordTable.experience]
        totalExperience = row[MemberExperienceRecordTable.totalExperience]
        creator = row[MemberExperienceRecordTable.creator]
        createTime = row[MemberExperienceRecordTable.createTime]
        updater = row[MemberExperienceRecordTable.updater]
        updateTime = row[MemberExperienceRecordTable.updateTime]
        deleted = row[MemberExperienceRecordTable.deleted]
        tenantId = row[MemberExperienceRecordTable.tenantId]
    }
}
