package im.hikaru.ruoyi.module.mp.dal.mysql.message

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpAutoReplyDO
import im.hikaru.ruoyi.module.mp.enums.message.MpAutoReplyMatchEnum
import im.hikaru.ruoyi.module.mp.enums.message.MpAutoReplyTypeEnum
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpAutoReplyDao {
    fun selectById(id: Long): MpAutoReplyDO? = transaction { MpAutoReplyTable.selectAll().where { conditions(MpAutoReplyTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpAutoReplyDO> = if (ids.isEmpty()) emptyList() else transaction { MpAutoReplyTable.selectAll().where { conditions(MpAutoReplyTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpAutoReplyDO> = transaction { MpAutoReplyTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpAutoReplyTable.selectAll().where { conditions() }.count() }
    fun selectPage(reqVO: MpMessagePageReqVO): PageResult<MpAutoReplyDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.accountId?.let { ops += MpAutoReplyTable.accountId eq it }
        reqVO.type?.toIntOrNull()?.let { ops += MpAutoReplyTable.type eq it }
        val rows = MpAutoReplyTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MpAutoReplyTable.id, SortOrder.DESC).map(::toEntity)
        rows.toPage(reqVO)
    }
    fun selectListByAppIdAndKeywordAll(appId: String, keyword: String): List<MpAutoReplyDO> = transaction {
        MpAutoReplyTable.selectAll().where {
            conditions(
                MpAutoReplyTable.appId eq appId,
                MpAutoReplyTable.type eq MpAutoReplyTypeEnum.KEYWORD.type,
                MpAutoReplyTable.requestMatch eq MpAutoReplyMatchEnum.ALL.match,
                MpAutoReplyTable.requestKeyword eq keyword,
            )
        }.map(::toEntity)
    }
    fun selectListByAppIdAndKeywordLike(appId: String, message: String): List<MpAutoReplyDO> = transaction {
        MpAutoReplyTable.selectAll().where {
            conditions(
                MpAutoReplyTable.appId eq appId,
                MpAutoReplyTable.type eq MpAutoReplyTypeEnum.KEYWORD.type,
                MpAutoReplyTable.requestMatch eq MpAutoReplyMatchEnum.LIKE.match,
            )
        }.map(::toEntity).filter { reply -> reply.requestKeyword?.let(message::contains) == true }
    }
    fun selectListByAppIdAndMessage(appId: String, messageType: String): List<MpAutoReplyDO> = transaction {
        MpAutoReplyTable.selectAll().where {
            conditions(
                MpAutoReplyTable.appId eq appId,
                MpAutoReplyTable.type eq MpAutoReplyTypeEnum.MESSAGE.type,
                MpAutoReplyTable.requestMessageType eq messageType,
            )
        }.map(::toEntity)
    }
    fun selectListByAppIdAndSubscribe(appId: String): List<MpAutoReplyDO> = transaction {
        MpAutoReplyTable.selectAll().where {
            conditions(MpAutoReplyTable.appId eq appId, MpAutoReplyTable.type eq MpAutoReplyTypeEnum.SUBSCRIBE.type)
        }.map(::toEntity)
    }
    fun selectByAccountIdAndSubscribe(accountId: Long): MpAutoReplyDO? = transaction {
        MpAutoReplyTable.selectAll().where {
            conditions(MpAutoReplyTable.accountId eq accountId, MpAutoReplyTable.type eq MpAutoReplyTypeEnum.SUBSCRIBE.type)
        }.singleOrNull()?.let(::toEntity)
    }
    fun selectByAccountIdAndMessage(accountId: Long, messageType: String?): MpAutoReplyDO? = transaction {
        MpAutoReplyTable.selectAll().where {
            conditions(
                MpAutoReplyTable.accountId eq accountId,
                MpAutoReplyTable.type eq MpAutoReplyTypeEnum.MESSAGE.type,
                MpAutoReplyTable.requestMessageType eq messageType,
            )
        }.singleOrNull()?.let(::toEntity)
    }
    fun selectByAccountIdAndKeyword(accountId: Long, keyword: String?): MpAutoReplyDO? = transaction {
        MpAutoReplyTable.selectAll().where {
            conditions(
                MpAutoReplyTable.accountId eq accountId,
                MpAutoReplyTable.type eq MpAutoReplyTypeEnum.KEYWORD.type,
                MpAutoReplyTable.requestKeyword eq keyword,
            )
        }.singleOrNull()?.let(::toEntity)
    }
    fun insert(entity: MpAutoReplyDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpAutoReplyTable.insert {
            it[MpAutoReplyTable.accountId] = entity.accountId
            it[MpAutoReplyTable.appId] = entity.appId
            it[MpAutoReplyTable.type] = entity.type
            it[MpAutoReplyTable.requestKeyword] = entity.requestKeyword
            it[MpAutoReplyTable.requestMatch] = entity.requestMatch
            it[MpAutoReplyTable.requestMessageType] = entity.requestMessageType
            it[MpAutoReplyTable.responseMessageType] = entity.responseMessageType
            it[MpAutoReplyTable.responseContent] = entity.responseContent
            it[MpAutoReplyTable.responseMediaId] = entity.responseMediaId
            it[MpAutoReplyTable.responseMediaUrl] = entity.responseMediaUrl
            it[MpAutoReplyTable.responseTitle] = entity.responseTitle
            it[MpAutoReplyTable.responseDescription] = entity.responseDescription
            it[MpAutoReplyTable.responseThumbMediaId] = entity.responseThumbMediaId
            it[MpAutoReplyTable.responseThumbMediaUrl] = entity.responseThumbMediaUrl
            it[MpAutoReplyTable.responseArticles] = entity.responseArticles
            it[MpAutoReplyTable.responseMusicUrl] = entity.responseMusicUrl
            it[MpAutoReplyTable.responseHqMusicUrl] = entity.responseHqMusicUrl
            it[MpAutoReplyTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpAutoReplyTable.creator] = entity.creator
            it[MpAutoReplyTable.updater] = entity.updater
            it[MpAutoReplyTable.createTime] = requireNotNull(entity.createTime)
            it[MpAutoReplyTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpAutoReplyTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpAutoReplyDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpAutoReplyTable.update(where = { conditions(MpAutoReplyTable.id eq id) }) {
            entity.accountId?.let { value -> it[MpAutoReplyTable.accountId] = value }
            entity.appId?.let { value -> it[MpAutoReplyTable.appId] = value }
            entity.type?.let { value -> it[MpAutoReplyTable.type] = value }
            entity.requestKeyword?.let { value -> it[MpAutoReplyTable.requestKeyword] = value }
            entity.requestMatch?.let { value -> it[MpAutoReplyTable.requestMatch] = value }
            entity.requestMessageType?.let { value -> it[MpAutoReplyTable.requestMessageType] = value }
            entity.responseMessageType?.let { value -> it[MpAutoReplyTable.responseMessageType] = value }
            entity.responseContent?.let { value -> it[MpAutoReplyTable.responseContent] = value }
            entity.responseMediaId?.let { value -> it[MpAutoReplyTable.responseMediaId] = value }
            entity.responseMediaUrl?.let { value -> it[MpAutoReplyTable.responseMediaUrl] = value }
            entity.responseTitle?.let { value -> it[MpAutoReplyTable.responseTitle] = value }
            entity.responseDescription?.let { value -> it[MpAutoReplyTable.responseDescription] = value }
            entity.responseThumbMediaId?.let { value -> it[MpAutoReplyTable.responseThumbMediaId] = value }
            entity.responseThumbMediaUrl?.let { value -> it[MpAutoReplyTable.responseThumbMediaUrl] = value }
            entity.responseArticles?.let { value -> it[MpAutoReplyTable.responseArticles] = value }
            entity.responseMusicUrl?.let { value -> it[MpAutoReplyTable.responseMusicUrl] = value }
            entity.responseHqMusicUrl?.let { value -> it[MpAutoReplyTable.responseHqMusicUrl] = value }
            entity.updater?.let { value -> it[MpAutoReplyTable.updater] = value }
            it[MpAutoReplyTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpAutoReplyTable.update(where = { conditions(MpAutoReplyTable.id eq id) }) { it[MpAutoReplyTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpAutoReplyTable.update(where = { conditions(MpAutoReplyTable.id inList ids) }) { it[MpAutoReplyTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpAutoReplyTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpAutoReplyTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpAutoReplyDO().apply {
        id = row[MpAutoReplyTable.id]
        accountId = row[MpAutoReplyTable.accountId]
        appId = row[MpAutoReplyTable.appId]
        type = row[MpAutoReplyTable.type]
        requestKeyword = row[MpAutoReplyTable.requestKeyword]
        requestMatch = row[MpAutoReplyTable.requestMatch]
        requestMessageType = row[MpAutoReplyTable.requestMessageType]
        responseMessageType = row[MpAutoReplyTable.responseMessageType]
        responseContent = row[MpAutoReplyTable.responseContent]
        responseMediaId = row[MpAutoReplyTable.responseMediaId]
        responseMediaUrl = row[MpAutoReplyTable.responseMediaUrl]
        responseTitle = row[MpAutoReplyTable.responseTitle]
        responseDescription = row[MpAutoReplyTable.responseDescription]
        responseThumbMediaId = row[MpAutoReplyTable.responseThumbMediaId]
        responseThumbMediaUrl = row[MpAutoReplyTable.responseThumbMediaUrl]
        responseArticles = row[MpAutoReplyTable.responseArticles]
        responseMusicUrl = row[MpAutoReplyTable.responseMusicUrl]
        responseHqMusicUrl = row[MpAutoReplyTable.responseHqMusicUrl]
        creator = row[MpAutoReplyTable.creator]
        createTime = row[MpAutoReplyTable.createTime]
        updater = row[MpAutoReplyTable.updater]
        updateTime = row[MpAutoReplyTable.updateTime]
        deleted = row[MpAutoReplyTable.deleted]
        tenantId = row[MpAutoReplyTable.tenantId]
    }

    private fun <T> List<T>.toPage(page: PageParam): PageResult<T> {
        val total = size.toLong()
        if (page.pageSize == PageParam.PAGE_SIZE_NONE) return PageResult(total, this)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        val list = if (from >= size) emptyList() else subList(from, minOf(from + page.pageSize, size))
        return PageResult(total, list)
    }
}
