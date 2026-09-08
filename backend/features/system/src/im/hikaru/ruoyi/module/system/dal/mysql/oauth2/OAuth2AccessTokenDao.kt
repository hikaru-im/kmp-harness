package im.hikaru.ruoyi.module.system.dal.mysql.oauth2

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import java.util.concurrent.Callable

object OAuth2AccessTokenDao {
    fun selectByAccessToken(accessToken: String): OAuth2AccessTokenDO? = TenantUtils.executeIgnore(Callable {
        transaction {
            OAuth2AccessTokenTable.selectAll().where { withoutTenant(OAuth2AccessTokenTable.accessToken eq accessToken) }
                .singleOrNull()?.let(::toEntity)
        }
    })

    fun selectListByRefreshToken(refreshToken: String): List<OAuth2AccessTokenDO> = transaction {
        OAuth2AccessTokenTable.selectAll().where { conditions(OAuth2AccessTokenTable.refreshToken eq refreshToken) }
            .map(::toEntity)
    }

    fun selectListByUserIdAndUserType(userId: Long, userType: Int): List<OAuth2AccessTokenDO> = transaction {
        OAuth2AccessTokenTable.selectAll().where {
            conditions(OAuth2AccessTokenTable.userId eq userId, OAuth2AccessTokenTable.userType eq userType)
        }.map(::toEntity)
    }

    fun selectPage(req: OAuth2AccessTokenPageReqVO, now: LocalDateTime): PageResult<OAuth2AccessTokenDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(OAuth2AccessTokenTable.expiresTime greater now)
        req.userId?.let { ops += OAuth2AccessTokenTable.userId eq it }
        req.userType?.let { ops += OAuth2AccessTokenTable.userType eq it }
        req.clientId?.takeIf { it.isNotBlank() }?.let { ops += OAuth2AccessTokenTable.clientId like "%$it%" }
        OAuth2AccessTokenTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(OAuth2AccessTokenTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: OAuth2AccessTokenDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            OAuth2AccessTokenTable.insert {
                it[userId] = requireNotNull(entity.userId)
                it[userType] = requireNotNull(entity.userType)
                it[userInfo] = entity.userInfo.orEmpty()
                it[accessToken] = requireNotNull(entity.accessToken)
                it[refreshToken] = requireNotNull(entity.refreshToken)
                it[clientId] = requireNotNull(entity.clientId)
                it[scopes] = entity.scopes
                it[expiresTime] = requireNotNull(entity.expiresTime)
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(OAuth2AccessTokenTable.id)
        }
        entity.id = id
        return id
    }

    fun deleteById(id: Long): Int = transaction {
        OAuth2AccessTokenTable.update(where = { conditions(OAuth2AccessTokenTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        OAuth2AccessTokenTable.update(where = { conditions(OAuth2AccessTokenTable.id inList ids) }) { it[deleted] = true }
    }

    fun deleteByExpiresTimeLt(expiresTime: LocalDateTime, limit: Int): Int = TenantUtils.executeIgnore(Callable {
        transaction {
            val ids = OAuth2AccessTokenTable.selectAll()
                .where { OAuth2AccessTokenTable.expiresTime less expiresTime }
                .limit(limit)
                .map { it[OAuth2AccessTokenTable.id] }
            if (ids.isEmpty()) 0 else OAuth2AccessTokenTable.deleteWhere { OAuth2AccessTokenTable.id inList ids }
        }
    })

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(OAuth2AccessTokenTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) {
            TenantContextHolder.getTenantId()?.let { ops += OAuth2AccessTokenTable.tenantId eq it }
        }
        ops += extra
        return ops.compoundAnd()
    }

    private fun withoutTenant(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(OAuth2AccessTokenTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow) = OAuth2AccessTokenDO().apply {
        id = row[OAuth2AccessTokenTable.id]
        userId = row[OAuth2AccessTokenTable.userId]
        userType = row[OAuth2AccessTokenTable.userType]
        userInfo = row[OAuth2AccessTokenTable.userInfo]
        accessToken = row[OAuth2AccessTokenTable.accessToken]
        refreshToken = row[OAuth2AccessTokenTable.refreshToken]
        clientId = row[OAuth2AccessTokenTable.clientId]
        scopes = row[OAuth2AccessTokenTable.scopes]
        expiresTime = row[OAuth2AccessTokenTable.expiresTime]
        tenantId = row[OAuth2AccessTokenTable.tenantId]
        creator = row[OAuth2AccessTokenTable.creator]
        createTime = row[OAuth2AccessTokenTable.createTime]
        updater = row[OAuth2AccessTokenTable.updater]
        updateTime = row[OAuth2AccessTokenTable.updateTime]
        deleted = row[OAuth2AccessTokenTable.deleted]
    }
}
