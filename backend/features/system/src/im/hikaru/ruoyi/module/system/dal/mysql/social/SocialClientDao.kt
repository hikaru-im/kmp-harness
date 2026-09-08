package im.hikaru.ruoyi.module.system.dal.mysql.social

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialClientDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object SocialClientDao {
    fun selectById(id: Long): SocialClientDO? = transaction {
        SocialClientTable.selectAll().where { conditions(SocialClientTable.id eq id) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectBySocialTypeAndUserType(socialType: Int, userType: Int): SocialClientDO? = transaction {
        SocialClientTable.selectAll().where {
            conditions(SocialClientTable.socialType eq socialType, SocialClientTable.userType eq userType)
        }.singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: SocialClientPageReqVO): PageResult<SocialClientDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.name?.takeIf { it.isNotBlank() }?.let { ops += SocialClientTable.name like "%$it%" }
        req.socialType?.let { ops += SocialClientTable.socialType eq it }
        req.userType?.let { ops += SocialClientTable.userType eq it }
        req.clientId?.takeIf { it.isNotBlank() }?.let { ops += SocialClientTable.clientId like "%$it%" }
        req.status?.let { ops += SocialClientTable.status eq it }
        SocialClientTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(SocialClientTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: SocialClientDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            SocialClientTable.insert {
                it[name] = requireNotNull(entity.name)
                it[socialType] = requireNotNull(entity.socialType)
                it[userType] = requireNotNull(entity.userType)
                it[clientId] = requireNotNull(entity.clientId)
                it[clientSecret] = requireNotNull(entity.clientSecret)
                it[agentId] = entity.agentId
                it[publicKey] = entity.publicKey
                it[status] = requireNotNull(entity.status)
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(SocialClientTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: SocialClientDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            SocialClientTable.update(where = { conditions(SocialClientTable.id eq requireNotNull(entity.id)) }) {
                entity.name?.let { value -> it[name] = value }
                entity.socialType?.let { value -> it[socialType] = value }
                entity.userType?.let { value -> it[userType] = value }
                entity.clientId?.let { value -> it[clientId] = value }
                entity.clientSecret?.let { value -> it[clientSecret] = value }
                it[agentId] = entity.agentId
                it[publicKey] = entity.publicKey
                entity.status?.let { value -> it[status] = value }
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        SocialClientTable.update(where = { conditions(SocialClientTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        SocialClientTable.update(where = { conditions(SocialClientTable.id inList ids) }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(SocialClientTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += SocialClientTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = SocialClientDO().apply {
        id = row[SocialClientTable.id]
        name = row[SocialClientTable.name]
        socialType = row[SocialClientTable.socialType]
        userType = row[SocialClientTable.userType]
        clientId = row[SocialClientTable.clientId]
        clientSecret = row[SocialClientTable.clientSecret]
        agentId = row[SocialClientTable.agentId]
        publicKey = row[SocialClientTable.publicKey]
        status = row[SocialClientTable.status]
        tenantId = row[SocialClientTable.tenantId]
        creator = row[SocialClientTable.creator]
        createTime = row[SocialClientTable.createTime]
        updater = row[SocialClientTable.updater]
        updateTime = row[SocialClientTable.updateTime]
        deleted = row[SocialClientTable.deleted]
    }
}
