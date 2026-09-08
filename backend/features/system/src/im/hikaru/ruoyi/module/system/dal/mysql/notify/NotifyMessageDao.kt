package im.hikaru.ruoyi.module.system.dal.mysql.notify

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyMessageDO
import kotlinx.datetime.toKotlinLocalDateTime
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

object NotifyMessageDao {
    fun selectById(id: Long): NotifyMessageDO? = transaction {
        NotifyMessageTable.selectAll().where { conditions(NotifyMessageTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: NotifyMessagePageReqVO): PageResult<NotifyMessageDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.userId?.let { ops += NotifyMessageTable.userId eq it }
        req.userType?.let { ops += NotifyMessageTable.userType eq it }
        req.templateCode?.takeIf { it.isNotBlank() }?.let { ops += NotifyMessageTable.templateCode like "%$it%" }
        req.templateType?.let { ops += NotifyMessageTable.templateType eq it }
        req.createTime?.getOrNull(0)?.let { ops += NotifyMessageTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += NotifyMessageTable.createTime lessEq it }
        NotifyMessageTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(NotifyMessageTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun selectMyPage(req: NotifyMessageMyPageReqVO, userId: Long, userType: Int): PageResult<NotifyMessageDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(
            NotifyMessageTable.userId eq userId,
            NotifyMessageTable.userType eq userType,
        )
        req.readStatus?.let { ops += NotifyMessageTable.readStatus eq it }
        req.createTime?.getOrNull(0)?.let { ops += NotifyMessageTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += NotifyMessageTable.createTime lessEq it }
        NotifyMessageTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(NotifyMessageTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun selectUnreadList(userId: Long, userType: Int, size: Int): List<NotifyMessageDO> = transaction {
        NotifyMessageTable.selectAll().where {
            conditions(
                NotifyMessageTable.userId eq userId,
                NotifyMessageTable.userType eq userType,
                NotifyMessageTable.readStatus eq false,
            )
        }.orderBy(NotifyMessageTable.id, SortOrder.DESC).limit(size).map(::toEntity)
    }

    fun selectUnreadCount(userId: Long, userType: Int): Long = transaction {
        NotifyMessageTable.selectAll().where {
            conditions(
                NotifyMessageTable.userId eq userId,
                NotifyMessageTable.userType eq userType,
                NotifyMessageTable.readStatus eq false,
            )
        }.count()
    }

    fun insert(entity: NotifyMessageDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            NotifyMessageTable.insert {
                it[userId] = requireNotNull(entity.userId)
                it[userType] = requireNotNull(entity.userType)
                it[templateId] = requireNotNull(entity.templateId)
                it[templateCode] = requireNotNull(entity.templateCode)
                it[templateType] = requireNotNull(entity.templateType)
                it[templateNickname] = requireNotNull(entity.templateNickname)
                it[templateContent] = requireNotNull(entity.templateContent)
                it[templateParams] = entity.templateParams.orEmpty()
                it[readStatus] = entity.readStatus ?: false
                it[readTime] = entity.readTime
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(NotifyMessageTable.id)
        }
        entity.id = id
        return id
    }

    fun updateRead(ids: Collection<Long>, userId: Long, userType: Int): Int {
        if (ids.isEmpty()) return 0
        return updateReadWhere(userId, userType, NotifyMessageTable.id inList ids)
    }

    fun updateAllRead(userId: Long, userType: Int): Int = updateReadWhere(userId, userType)

    private fun updateReadWhere(userId: Long, userType: Int, vararg extra: Op<Boolean>): Int = transaction {
        val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()
        NotifyMessageTable.update(where = {
            conditions(
                NotifyMessageTable.userId eq userId,
                NotifyMessageTable.userType eq userType,
                NotifyMessageTable.readStatus eq false,
                *extra,
            )
        }) {
            it[readStatus] = true
            it[readTime] = now
            it[updateTime] = now
        }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(NotifyMessageTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += NotifyMessageTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = NotifyMessageDO().apply {
        id = row[NotifyMessageTable.id]
        userId = row[NotifyMessageTable.userId]
        userType = row[NotifyMessageTable.userType]
        templateId = row[NotifyMessageTable.templateId]
        templateCode = row[NotifyMessageTable.templateCode]
        templateType = row[NotifyMessageTable.templateType]
        templateNickname = row[NotifyMessageTable.templateNickname]
        templateContent = row[NotifyMessageTable.templateContent]
        templateParams = row[NotifyMessageTable.templateParams]
        readStatus = row[NotifyMessageTable.readStatus]
        readTime = row[NotifyMessageTable.readTime]
        tenantId = row[NotifyMessageTable.tenantId]
        creator = row[NotifyMessageTable.creator]
        createTime = row[NotifyMessageTable.createTime]
        updater = row[NotifyMessageTable.updater]
        updateTime = row[NotifyMessageTable.updateTime]
        deleted = row[NotifyMessageTable.deleted]
    }
}
