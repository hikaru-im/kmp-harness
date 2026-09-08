package im.hikaru.ruoyi.module.system.dal.mysql.social

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialUserBindDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object SocialUserBindDao {
    fun selectByUserTypeAndSocialUserId(userType: Int, socialUserId: Long): SocialUserBindDO? = transaction {
        SocialUserBindTable.selectAll().where {
            conditions(SocialUserBindTable.userType eq userType, SocialUserBindTable.socialUserId eq socialUserId)
        }.singleOrNull()?.let(::toEntity)
    }

    fun selectListByUserIdAndUserType(userId: Long, userType: Int): List<SocialUserBindDO> = transaction {
        SocialUserBindTable.selectAll().where {
            conditions(SocialUserBindTable.userId eq userId, SocialUserBindTable.userType eq userType)
        }.map(::toEntity)
    }

    fun selectByUserIdAndUserTypeAndSocialType(userId: Long, userType: Int, socialType: Int): SocialUserBindDO? = transaction {
        SocialUserBindTable.selectAll().where {
            conditions(
                SocialUserBindTable.userId eq userId,
                SocialUserBindTable.userType eq userType,
                SocialUserBindTable.socialType eq socialType,
            )
        }.singleOrNull()?.let(::toEntity)
    }

    fun insert(entity: SocialUserBindDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            SocialUserBindTable.insert {
                it[userId] = requireNotNull(entity.userId)
                it[userType] = requireNotNull(entity.userType)
                it[socialUserId] = requireNotNull(entity.socialUserId)
                it[socialType] = requireNotNull(entity.socialType)
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(SocialUserBindTable.id)
        }
        entity.id = id
        return id
    }

    fun deleteByUserTypeAndUserIdAndSocialType(userType: Int, userId: Long, socialType: Int): Int = transaction {
        SocialUserBindTable.update(where = {
            conditions(
                SocialUserBindTable.userType eq userType,
                SocialUserBindTable.userId eq userId,
                SocialUserBindTable.socialType eq socialType,
            )
        }) { it[deleted] = true }
    }

    fun deleteByUserTypeAndSocialUserId(userType: Int, socialUserId: Long): Int = transaction {
        SocialUserBindTable.update(where = {
            conditions(SocialUserBindTable.userType eq userType, SocialUserBindTable.socialUserId eq socialUserId)
        }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(SocialUserBindTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += SocialUserBindTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = SocialUserBindDO().apply {
        id = row[SocialUserBindTable.id]
        userId = row[SocialUserBindTable.userId]
        userType = row[SocialUserBindTable.userType]
        socialUserId = row[SocialUserBindTable.socialUserId]
        socialType = row[SocialUserBindTable.socialType]
        tenantId = row[SocialUserBindTable.tenantId]
        creator = row[SocialUserBindTable.creator]
        createTime = row[SocialUserBindTable.createTime]
        updater = row[SocialUserBindTable.updater]
        updateTime = row[SocialUserBindTable.updateTime]
        deleted = row[SocialUserBindTable.deleted]
    }
}
