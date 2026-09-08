package im.hikaru.ruoyi.module.system.dal.mysql.oauth2

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ClientDO
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

object OAuth2ClientDao {
    fun selectById(id: Long): OAuth2ClientDO? = transaction {
        OAuth2ClientTable.selectAll().where { conditions(OAuth2ClientTable.id eq id) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectByClientId(clientId: String): OAuth2ClientDO? = transaction {
        OAuth2ClientTable.selectAll().where { conditions(OAuth2ClientTable.clientId eq clientId) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: OAuth2ClientPageReqVO): PageResult<OAuth2ClientDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.name?.takeIf { it.isNotBlank() }?.let { ops += OAuth2ClientTable.name like "%$it%" }
        req.status?.let { ops += OAuth2ClientTable.status eq it }
        OAuth2ClientTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(OAuth2ClientTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: OAuth2ClientDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            OAuth2ClientTable.insert {
                it[clientId] = requireNotNull(entity.clientId)
                it[secret] = requireNotNull(entity.secret)
                it[name] = requireNotNull(entity.name)
                it[logo] = requireNotNull(entity.logo)
                it[description] = entity.description
                it[status] = requireNotNull(entity.status)
                it[accessTokenValiditySeconds] = requireNotNull(entity.accessTokenValiditySeconds)
                it[refreshTokenValiditySeconds] = requireNotNull(entity.refreshTokenValiditySeconds)
                it[redirectUris] = requireNotNull(entity.redirectUris)
                it[authorizedGrantTypes] = requireNotNull(entity.authorizedGrantTypes)
                it[scopes] = entity.scopes
                it[autoApproveScopes] = entity.autoApproveScopes
                it[authorities] = entity.authorities
                it[resourceIds] = entity.resourceIds
                it[additionalInformation] = entity.additionalInformation
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(OAuth2ClientTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: OAuth2ClientDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        transaction {
            OAuth2ClientTable.update(where = { conditions(OAuth2ClientTable.id eq id) }) {
                entity.clientId?.let { value -> it[clientId] = value }
                entity.secret?.let { value -> it[secret] = value }
                entity.name?.let { value -> it[name] = value }
                entity.logo?.let { value -> it[logo] = value }
                it[description] = entity.description
                entity.status?.let { value -> it[status] = value }
                entity.accessTokenValiditySeconds?.let { value -> it[accessTokenValiditySeconds] = value }
                entity.refreshTokenValiditySeconds?.let { value -> it[refreshTokenValiditySeconds] = value }
                entity.redirectUris?.let { value -> it[redirectUris] = value }
                entity.authorizedGrantTypes?.let { value -> it[authorizedGrantTypes] = value }
                it[scopes] = entity.scopes
                it[autoApproveScopes] = entity.autoApproveScopes
                it[authorities] = entity.authorities
                it[resourceIds] = entity.resourceIds
                it[additionalInformation] = entity.additionalInformation
                entity.updater?.let { value -> it[updater] = value }
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        OAuth2ClientTable.update(where = { conditions(OAuth2ClientTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        OAuth2ClientTable.update(where = { conditions(OAuth2ClientTable.id inList ids) }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(OAuth2ClientTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow) = OAuth2ClientDO().apply {
        id = row[OAuth2ClientTable.id]
        clientId = row[OAuth2ClientTable.clientId]
        secret = row[OAuth2ClientTable.secret]
        name = row[OAuth2ClientTable.name]
        logo = row[OAuth2ClientTable.logo]
        description = row[OAuth2ClientTable.description]
        status = row[OAuth2ClientTable.status]
        accessTokenValiditySeconds = row[OAuth2ClientTable.accessTokenValiditySeconds]
        refreshTokenValiditySeconds = row[OAuth2ClientTable.refreshTokenValiditySeconds]
        redirectUris = row[OAuth2ClientTable.redirectUris]
        authorizedGrantTypes = row[OAuth2ClientTable.authorizedGrantTypes]
        scopes = row[OAuth2ClientTable.scopes]
        autoApproveScopes = row[OAuth2ClientTable.autoApproveScopes]
        authorities = row[OAuth2ClientTable.authorities]
        resourceIds = row[OAuth2ClientTable.resourceIds]
        additionalInformation = row[OAuth2ClientTable.additionalInformation]
        creator = row[OAuth2ClientTable.creator]
        createTime = row[OAuth2ClientTable.createTime]
        updater = row[OAuth2ClientTable.updater]
        updateTime = row[OAuth2ClientTable.updateTime]
        deleted = row[OAuth2ClientTable.deleted]
    }
}
