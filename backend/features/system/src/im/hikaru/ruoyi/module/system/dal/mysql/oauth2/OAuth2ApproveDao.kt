package im.hikaru.ruoyi.module.system.dal.mysql.oauth2

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ApproveDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object OAuth2ApproveDao {
    fun selectListByUserIdAndUserTypeAndClientId(userId: Long, userType: Int, clientId: String): List<OAuth2ApproveDO> =
        transaction {
            OAuth2ApproveTable.selectAll().where {
                conditions(
                    OAuth2ApproveTable.userId eq userId,
                    OAuth2ApproveTable.userType eq userType,
                    OAuth2ApproveTable.clientId eq clientId,
                )
            }.map(::toEntity)
        }

    fun insert(entity: OAuth2ApproveDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            OAuth2ApproveTable.insert {
                it[userId] = requireNotNull(entity.userId)
                it[userType] = requireNotNull(entity.userType)
                it[clientId] = requireNotNull(entity.clientId)
                it[scope] = requireNotNull(entity.scope)
                it[approved] = requireNotNull(entity.approved)
                it[expiresTime] = requireNotNull(entity.expiresTime)
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(OAuth2ApproveTable.id)
        }
        entity.id = id
        return id
    }

    fun update(entity: OAuth2ApproveDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val userId = requireNotNull(entity.userId)
        val userType = requireNotNull(entity.userType)
        val clientId = requireNotNull(entity.clientId)
        val scope = requireNotNull(entity.scope)
        return transaction {
            OAuth2ApproveTable.update(where = {
                conditions(
                    OAuth2ApproveTable.userId eq userId,
                    OAuth2ApproveTable.userType eq userType,
                    OAuth2ApproveTable.clientId eq clientId,
                    OAuth2ApproveTable.scope eq scope,
                )
            }) {
                entity.approved?.let { value -> it[approved] = value }
                entity.expiresTime?.let { value -> it[expiresTime] = value }
                entity.updater?.let { value -> it[updater] = value }
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(OAuth2ApproveTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += OAuth2ApproveTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = OAuth2ApproveDO().apply {
        id = row[OAuth2ApproveTable.id]
        userId = row[OAuth2ApproveTable.userId]
        userType = row[OAuth2ApproveTable.userType]
        clientId = row[OAuth2ApproveTable.clientId]
        scope = row[OAuth2ApproveTable.scope]
        approved = row[OAuth2ApproveTable.approved]
        expiresTime = row[OAuth2ApproveTable.expiresTime]
        tenantId = row[OAuth2ApproveTable.tenantId]
        creator = row[OAuth2ApproveTable.creator]
        createTime = row[OAuth2ApproveTable.createTime]
        updater = row[OAuth2ApproveTable.updater]
        updateTime = row[OAuth2ApproveTable.updateTime]
        deleted = row[OAuth2ApproveTable.deleted]
    }
}
