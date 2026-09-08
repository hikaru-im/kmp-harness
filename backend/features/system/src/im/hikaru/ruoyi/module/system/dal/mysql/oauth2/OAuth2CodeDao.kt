package im.hikaru.ruoyi.module.system.dal.mysql.oauth2

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2CodeDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object OAuth2CodeDao {
    fun selectByCode(code: String): OAuth2CodeDO? = transaction {
        OAuth2CodeTable.selectAll().where { conditions(OAuth2CodeTable.code eq code) }
            .singleOrNull()?.let(::toEntity)
    }

    fun insert(entity: OAuth2CodeDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            OAuth2CodeTable.insert {
                it[userId] = requireNotNull(entity.userId)
                it[userType] = requireNotNull(entity.userType)
                it[code] = requireNotNull(entity.code)
                it[clientId] = requireNotNull(entity.clientId)
                it[scopes] = entity.scopes
                it[expiresTime] = requireNotNull(entity.expiresTime)
                it[redirectUri] = entity.redirectUri
                it[state] = entity.state.orEmpty()
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(OAuth2CodeTable.id)
        }
        entity.id = id
        return id
    }

    fun deleteById(id: Long): Int = transaction {
        OAuth2CodeTable.update(where = { conditions(OAuth2CodeTable.id eq id) }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(OAuth2CodeTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += OAuth2CodeTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = OAuth2CodeDO().apply {
        id = row[OAuth2CodeTable.id]
        userId = row[OAuth2CodeTable.userId]
        userType = row[OAuth2CodeTable.userType]
        code = row[OAuth2CodeTable.code]
        clientId = row[OAuth2CodeTable.clientId]
        scopes = row[OAuth2CodeTable.scopes]
        expiresTime = row[OAuth2CodeTable.expiresTime]
        redirectUri = row[OAuth2CodeTable.redirectUri]
        state = row[OAuth2CodeTable.state]
        tenantId = row[OAuth2CodeTable.tenantId]
        creator = row[OAuth2CodeTable.creator]
        createTime = row[OAuth2CodeTable.createTime]
        updater = row[OAuth2CodeTable.updater]
        updateTime = row[OAuth2CodeTable.updateTime]
        deleted = row[OAuth2CodeTable.deleted]
    }
}
