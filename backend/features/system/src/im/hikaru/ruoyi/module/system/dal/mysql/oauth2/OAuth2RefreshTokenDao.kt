package im.hikaru.ruoyi.module.system.dal.mysql.oauth2

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import java.util.concurrent.Callable

object OAuth2RefreshTokenDao {
    fun selectByRefreshToken(refreshToken: String): OAuth2RefreshTokenDO? = TenantUtils.executeIgnore(Callable {
        transaction {
            OAuth2RefreshTokenTable.selectAll().where { withoutTenant(OAuth2RefreshTokenTable.refreshToken eq refreshToken) }
                .singleOrNull()?.let(::toEntity)
        }
    })

    fun insert(entity: OAuth2RefreshTokenDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            OAuth2RefreshTokenTable.insert {
                it[userId] = requireNotNull(entity.userId)
                it[refreshToken] = requireNotNull(entity.refreshToken)
                it[userType] = requireNotNull(entity.userType)
                it[clientId] = requireNotNull(entity.clientId)
                it[scopes] = entity.scopes
                it[expiresTime] = requireNotNull(entity.expiresTime)
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(OAuth2RefreshTokenTable.id)
        }
        entity.id = id
        return id
    }

    fun deleteById(id: Long): Int = transaction {
        OAuth2RefreshTokenTable.update(where = { conditions(OAuth2RefreshTokenTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByRefreshToken(refreshToken: String): Int = transaction {
        OAuth2RefreshTokenTable.update(where = { conditions(OAuth2RefreshTokenTable.refreshToken eq refreshToken) }) {
            it[deleted] = true
        }
    }

    fun deleteByExpiresTimeLt(expiresTime: LocalDateTime, limit: Int): Int = TenantUtils.executeIgnore(Callable {
        transaction {
            val ids = OAuth2RefreshTokenTable.selectAll()
                .where { OAuth2RefreshTokenTable.expiresTime less expiresTime }
                .limit(limit)
                .map { it[OAuth2RefreshTokenTable.id] }
            if (ids.isEmpty()) 0 else OAuth2RefreshTokenTable.deleteWhere { OAuth2RefreshTokenTable.id inList ids }
        }
    })

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(OAuth2RefreshTokenTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) {
            TenantContextHolder.getTenantId()?.let { ops += OAuth2RefreshTokenTable.tenantId eq it }
        }
        ops += extra
        return ops.compoundAnd()
    }

    private fun withoutTenant(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(OAuth2RefreshTokenTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow) = OAuth2RefreshTokenDO().apply {
        id = row[OAuth2RefreshTokenTable.id]
        userId = row[OAuth2RefreshTokenTable.userId]
        refreshToken = row[OAuth2RefreshTokenTable.refreshToken]
        userType = row[OAuth2RefreshTokenTable.userType]
        clientId = row[OAuth2RefreshTokenTable.clientId]
        scopes = row[OAuth2RefreshTokenTable.scopes]
        expiresTime = row[OAuth2RefreshTokenTable.expiresTime]
        tenantId = row[OAuth2RefreshTokenTable.tenantId]
        creator = row[OAuth2RefreshTokenTable.creator]
        createTime = row[OAuth2RefreshTokenTable.createTime]
        updater = row[OAuth2RefreshTokenTable.updater]
        updateTime = row[OAuth2RefreshTokenTable.updateTime]
        deleted = row[OAuth2RefreshTokenTable.deleted]
    }
}
