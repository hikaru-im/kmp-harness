package im.hikaru.ruoyi.module.system.dal.mysql.social

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialUserDO
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
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object SocialUserDao {
    fun selectById(id: Long): SocialUserDO? = transaction {
        SocialUserTable.selectAll().where { conditions(SocialUserTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<SocialUserDO> = if (ids.isEmpty()) emptyList() else transaction {
        SocialUserTable.selectAll().where { conditions(SocialUserTable.id inList ids) }.map(::toEntity)
    }

    fun selectByTypeAndCodeAndState(type: Int, code: String, state: String?): SocialUserDO? = transaction {
        SocialUserTable.selectAll().where {
            conditions(SocialUserTable.type eq type, SocialUserTable.code eq code, SocialUserTable.state eq state)
        }.singleOrNull()?.let(::toEntity)
    }

    fun selectByTypeAndOpenid(type: Int, openid: String): SocialUserDO? = transaction {
        SocialUserTable.selectAll().where {
            conditions(SocialUserTable.type eq type, SocialUserTable.openid eq openid)
        }.singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: SocialUserPageReqVO): PageResult<SocialUserDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.type?.let { ops += SocialUserTable.type eq it }
        req.nickname?.takeIf { it.isNotBlank() }?.let { ops += SocialUserTable.nickname like "%$it%" }
        req.openid?.takeIf { it.isNotBlank() }?.let { ops += SocialUserTable.openid like "%$it%" }
        req.createTime?.getOrNull(0)?.let { ops += SocialUserTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += SocialUserTable.createTime lessEq it }
        SocialUserTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(SocialUserTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: SocialUserDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            SocialUserTable.insert {
                it[type] = requireNotNull(entity.type)
                it[openid] = requireNotNull(entity.openid)
                it[token] = entity.token
                it[rawTokenInfo] = requireNotNull(entity.rawTokenInfo)
                it[nickname] = requireNotNull(entity.nickname)
                it[avatar] = entity.avatar
                it[rawUserInfo] = requireNotNull(entity.rawUserInfo)
                it[code] = requireNotNull(entity.code)
                it[state] = entity.state
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(SocialUserTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: SocialUserDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            SocialUserTable.update(where = { conditions(SocialUserTable.id eq requireNotNull(entity.id)) }) {
                it[type] = requireNotNull(entity.type)
                it[openid] = requireNotNull(entity.openid)
                it[token] = entity.token
                it[rawTokenInfo] = requireNotNull(entity.rawTokenInfo)
                it[nickname] = requireNotNull(entity.nickname)
                it[avatar] = entity.avatar
                it[rawUserInfo] = requireNotNull(entity.rawUserInfo)
                it[code] = requireNotNull(entity.code)
                it[state] = entity.state
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(SocialUserTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += SocialUserTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = SocialUserDO().apply {
        id = row[SocialUserTable.id]
        type = row[SocialUserTable.type]
        openid = row[SocialUserTable.openid]
        token = row[SocialUserTable.token]
        rawTokenInfo = row[SocialUserTable.rawTokenInfo]
        nickname = row[SocialUserTable.nickname]
        avatar = row[SocialUserTable.avatar]
        rawUserInfo = row[SocialUserTable.rawUserInfo]
        code = row[SocialUserTable.code]
        state = row[SocialUserTable.state]
        tenantId = row[SocialUserTable.tenantId]
        creator = row[SocialUserTable.creator]
        createTime = row[SocialUserTable.createTime]
        updater = row[SocialUserTable.updater]
        updateTime = row[SocialUserTable.updateTime]
        deleted = row[SocialUserTable.deleted]
    }
}
