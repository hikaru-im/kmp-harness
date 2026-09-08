package im.hikaru.ruoyi.module.mp.dal.mysql.message

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpMessageDao {
    fun selectById(id: Long): MpMessageDO? = transaction { MpMessageTable.selectAll().where { conditions(MpMessageTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpMessageDO> = if (ids.isEmpty()) emptyList() else transaction { MpMessageTable.selectAll().where { conditions(MpMessageTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpMessageDO> = transaction { MpMessageTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpMessageTable.selectAll().where { conditions() }.count() }
    fun selectPage(reqVO: MpMessagePageReqVO): PageResult<MpMessageDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.accountId?.let { ops += MpMessageTable.accountId eq it }
        reqVO.type?.takeIf(String::isNotBlank)?.let { ops += MpMessageTable.type eq it }
        reqVO.openid?.takeIf(String::isNotBlank)?.let { ops += MpMessageTable.openid eq it }
        reqVO.userId?.toLongOrNull()?.let { ops += MpMessageTable.userId eq it }
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MpMessageTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MpMessageTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        val rows = MpMessageTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MpMessageTable.id, SortOrder.DESC).map(::toEntity)
        rows.toPage(reqVO)
    }
    fun insert(entity: MpMessageDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpMessageTable.insert {
            it[MpMessageTable.msgId] = entity.msgId
            it[MpMessageTable.accountId] = entity.accountId
            it[MpMessageTable.appId] = entity.appId
            it[MpMessageTable.userId] = entity.userId
            it[MpMessageTable.openid] = entity.openid
            it[MpMessageTable.type] = entity.type
            it[MpMessageTable.sendFrom] = entity.sendFrom
            it[MpMessageTable.content] = entity.content
            it[MpMessageTable.mediaId] = entity.mediaId
            it[MpMessageTable.mediaUrl] = entity.mediaUrl
            it[MpMessageTable.recognition] = entity.recognition
            it[MpMessageTable.format] = entity.format
            it[MpMessageTable.title] = entity.title
            it[MpMessageTable.description] = entity.description
            it[MpMessageTable.thumbMediaId] = entity.thumbMediaId
            it[MpMessageTable.thumbMediaUrl] = entity.thumbMediaUrl
            it[MpMessageTable.url] = entity.url
            it[MpMessageTable.locationX] = entity.locationX
            it[MpMessageTable.locationY] = entity.locationY
            it[MpMessageTable.scale] = entity.scale
            it[MpMessageTable.label] = entity.label
            it[MpMessageTable.articles] = entity.articles
            it[MpMessageTable.musicUrl] = entity.musicUrl
            it[MpMessageTable.hqMusicUrl] = entity.hqMusicUrl
            it[MpMessageTable.event] = entity.event
            it[MpMessageTable.eventKey] = entity.eventKey
            it[MpMessageTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpMessageTable.creator] = entity.creator
            it[MpMessageTable.updater] = entity.updater
            it[MpMessageTable.createTime] = requireNotNull(entity.createTime)
            it[MpMessageTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpMessageTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpMessageDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpMessageTable.update(where = { conditions(MpMessageTable.id eq id) }) {
            entity.msgId?.let { value -> it[MpMessageTable.msgId] = value }
            entity.accountId?.let { value -> it[MpMessageTable.accountId] = value }
            entity.appId?.let { value -> it[MpMessageTable.appId] = value }
            entity.userId?.let { value -> it[MpMessageTable.userId] = value }
            entity.openid?.let { value -> it[MpMessageTable.openid] = value }
            entity.type?.let { value -> it[MpMessageTable.type] = value }
            entity.sendFrom?.let { value -> it[MpMessageTable.sendFrom] = value }
            entity.content?.let { value -> it[MpMessageTable.content] = value }
            entity.mediaId?.let { value -> it[MpMessageTable.mediaId] = value }
            entity.mediaUrl?.let { value -> it[MpMessageTable.mediaUrl] = value }
            entity.recognition?.let { value -> it[MpMessageTable.recognition] = value }
            entity.format?.let { value -> it[MpMessageTable.format] = value }
            entity.title?.let { value -> it[MpMessageTable.title] = value }
            entity.description?.let { value -> it[MpMessageTable.description] = value }
            entity.thumbMediaId?.let { value -> it[MpMessageTable.thumbMediaId] = value }
            entity.thumbMediaUrl?.let { value -> it[MpMessageTable.thumbMediaUrl] = value }
            entity.url?.let { value -> it[MpMessageTable.url] = value }
            entity.locationX?.let { value -> it[MpMessageTable.locationX] = value }
            entity.locationY?.let { value -> it[MpMessageTable.locationY] = value }
            entity.scale?.let { value -> it[MpMessageTable.scale] = value }
            entity.label?.let { value -> it[MpMessageTable.label] = value }
            entity.articles?.let { value -> it[MpMessageTable.articles] = value }
            entity.musicUrl?.let { value -> it[MpMessageTable.musicUrl] = value }
            entity.hqMusicUrl?.let { value -> it[MpMessageTable.hqMusicUrl] = value }
            entity.event?.let { value -> it[MpMessageTable.event] = value }
            entity.eventKey?.let { value -> it[MpMessageTable.eventKey] = value }
            entity.updater?.let { value -> it[MpMessageTable.updater] = value }
            it[MpMessageTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpMessageTable.update(where = { conditions(MpMessageTable.id eq id) }) { it[MpMessageTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpMessageTable.update(where = { conditions(MpMessageTable.id inList ids) }) { it[MpMessageTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpMessageTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpMessageTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpMessageDO().apply {
        id = row[MpMessageTable.id]
        msgId = row[MpMessageTable.msgId]
        accountId = row[MpMessageTable.accountId]
        appId = row[MpMessageTable.appId]
        userId = row[MpMessageTable.userId]
        openid = row[MpMessageTable.openid]
        type = row[MpMessageTable.type]
        sendFrom = row[MpMessageTable.sendFrom]
        content = row[MpMessageTable.content]
        mediaId = row[MpMessageTable.mediaId]
        mediaUrl = row[MpMessageTable.mediaUrl]
        recognition = row[MpMessageTable.recognition]
        format = row[MpMessageTable.format]
        title = row[MpMessageTable.title]
        description = row[MpMessageTable.description]
        thumbMediaId = row[MpMessageTable.thumbMediaId]
        thumbMediaUrl = row[MpMessageTable.thumbMediaUrl]
        url = row[MpMessageTable.url]
        locationX = row[MpMessageTable.locationX]
        locationY = row[MpMessageTable.locationY]
        scale = row[MpMessageTable.scale]
        label = row[MpMessageTable.label]
        articles = row[MpMessageTable.articles]
        musicUrl = row[MpMessageTable.musicUrl]
        hqMusicUrl = row[MpMessageTable.hqMusicUrl]
        event = row[MpMessageTable.event]
        eventKey = row[MpMessageTable.eventKey]
        creator = row[MpMessageTable.creator]
        createTime = row[MpMessageTable.createTime]
        updater = row[MpMessageTable.updater]
        updateTime = row[MpMessageTable.updateTime]
        deleted = row[MpMessageTable.deleted]
        tenantId = row[MpMessageTable.tenantId]
    }

    private fun <T> List<T>.toPage(page: PageParam): PageResult<T> {
        val total = size.toLong()
        if (page.pageSize == PageParam.PAGE_SIZE_NONE) return PageResult(total, this)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        val list = if (from >= size) emptyList() else subList(from, minOf(from + page.pageSize, size))
        return PageResult(total, list)
    }
}
