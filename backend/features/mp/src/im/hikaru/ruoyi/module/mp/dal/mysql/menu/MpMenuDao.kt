package im.hikaru.ruoyi.module.mp.dal.mysql.menu

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.mp.dal.dataobject.menu.MpMenuDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpMenuDao {
    fun selectById(id: Long): MpMenuDO? = transaction { MpMenuTable.selectAll().where { conditions(MpMenuTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpMenuDO> = if (ids.isEmpty()) emptyList() else transaction { MpMenuTable.selectAll().where { conditions(MpMenuTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpMenuDO> = transaction { MpMenuTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpMenuTable.selectAll().where { conditions() }.count() }
    fun selectByAppIdAndMenuKey(appId: String, menuKey: String): MpMenuDO? = transaction {
        MpMenuTable.selectAll().where { conditions(MpMenuTable.appId eq appId, MpMenuTable.menuKey eq menuKey) }
            .singleOrNull()?.let(::toEntity)
    }
    fun selectListByAccountId(accountId: Long): List<MpMenuDO> = transaction {
        MpMenuTable.selectAll().where { conditions(MpMenuTable.accountId eq accountId) }
            .orderBy(MpMenuTable.id, SortOrder.ASC).map(::toEntity)
    }
    fun deleteByAccountId(accountId: Long): Int = transaction {
        MpMenuTable.update(where = { conditions(MpMenuTable.accountId eq accountId) }) { it[MpMenuTable.deleted] = true }
    }
    fun insert(entity: MpMenuDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpMenuTable.insert {
            it[MpMenuTable.accountId] = entity.accountId
            it[MpMenuTable.appId] = entity.appId
            it[MpMenuTable.name] = entity.name
            it[MpMenuTable.menuKey] = entity.menuKey
            it[MpMenuTable.parentId] = entity.parentId?.toString()
            it[MpMenuTable.type] = entity.type
            it[MpMenuTable.url] = entity.url
            it[MpMenuTable.miniProgramAppId] = entity.miniProgramAppId
            it[MpMenuTable.miniProgramPagePath] = entity.miniProgramPagePath
            it[MpMenuTable.articleId] = entity.articleId
            it[MpMenuTable.replyMessageType] = entity.replyMessageType
            it[MpMenuTable.replyContent] = entity.replyContent
            it[MpMenuTable.replyMediaId] = entity.replyMediaId
            it[MpMenuTable.replyMediaUrl] = entity.replyMediaUrl
            it[MpMenuTable.replyTitle] = entity.replyTitle
            it[MpMenuTable.replyDescription] = entity.replyDescription
            it[MpMenuTable.replyThumbMediaId] = entity.replyThumbMediaId
            it[MpMenuTable.replyThumbMediaUrl] = entity.replyThumbMediaUrl
            it[MpMenuTable.replyArticles] = entity.replyArticles
            it[MpMenuTable.replyMusicUrl] = entity.replyMusicUrl
            it[MpMenuTable.replyHqMusicUrl] = entity.replyHqMusicUrl
            it[MpMenuTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpMenuTable.creator] = entity.creator
            it[MpMenuTable.updater] = entity.updater
            it[MpMenuTable.createTime] = requireNotNull(entity.createTime)
            it[MpMenuTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpMenuTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpMenuDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpMenuTable.update(where = { conditions(MpMenuTable.id eq id) }) {
            entity.accountId?.let { value -> it[MpMenuTable.accountId] = value }
            entity.appId?.let { value -> it[MpMenuTable.appId] = value }
            entity.name?.let { value -> it[MpMenuTable.name] = value }
            entity.menuKey?.let { value -> it[MpMenuTable.menuKey] = value }
            entity.parentId?.let { value -> it[MpMenuTable.parentId] = value.toString() }
            entity.type?.let { value -> it[MpMenuTable.type] = value }
            entity.url?.let { value -> it[MpMenuTable.url] = value }
            entity.miniProgramAppId?.let { value -> it[MpMenuTable.miniProgramAppId] = value }
            entity.miniProgramPagePath?.let { value -> it[MpMenuTable.miniProgramPagePath] = value }
            entity.articleId?.let { value -> it[MpMenuTable.articleId] = value }
            entity.replyMessageType?.let { value -> it[MpMenuTable.replyMessageType] = value }
            entity.replyContent?.let { value -> it[MpMenuTable.replyContent] = value }
            entity.replyMediaId?.let { value -> it[MpMenuTable.replyMediaId] = value }
            entity.replyMediaUrl?.let { value -> it[MpMenuTable.replyMediaUrl] = value }
            entity.replyTitle?.let { value -> it[MpMenuTable.replyTitle] = value }
            entity.replyDescription?.let { value -> it[MpMenuTable.replyDescription] = value }
            entity.replyThumbMediaId?.let { value -> it[MpMenuTable.replyThumbMediaId] = value }
            entity.replyThumbMediaUrl?.let { value -> it[MpMenuTable.replyThumbMediaUrl] = value }
            entity.replyArticles?.let { value -> it[MpMenuTable.replyArticles] = value }
            entity.replyMusicUrl?.let { value -> it[MpMenuTable.replyMusicUrl] = value }
            entity.replyHqMusicUrl?.let { value -> it[MpMenuTable.replyHqMusicUrl] = value }
            entity.updater?.let { value -> it[MpMenuTable.updater] = value }
            it[MpMenuTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpMenuTable.update(where = { conditions(MpMenuTable.id eq id) }) { it[MpMenuTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpMenuTable.update(where = { conditions(MpMenuTable.id inList ids) }) { it[MpMenuTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpMenuTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpMenuTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpMenuDO().apply {
        id = row[MpMenuTable.id]
        accountId = row[MpMenuTable.accountId]
        appId = row[MpMenuTable.appId]
        name = row[MpMenuTable.name]
        menuKey = row[MpMenuTable.menuKey]
        parentId = row[MpMenuTable.parentId]?.toLongOrNull()
        type = row[MpMenuTable.type]
        url = row[MpMenuTable.url]
        miniProgramAppId = row[MpMenuTable.miniProgramAppId]
        miniProgramPagePath = row[MpMenuTable.miniProgramPagePath]
        articleId = row[MpMenuTable.articleId]
        replyMessageType = row[MpMenuTable.replyMessageType]
        replyContent = row[MpMenuTable.replyContent]
        replyMediaId = row[MpMenuTable.replyMediaId]
        replyMediaUrl = row[MpMenuTable.replyMediaUrl]
        replyTitle = row[MpMenuTable.replyTitle]
        replyDescription = row[MpMenuTable.replyDescription]
        replyThumbMediaId = row[MpMenuTable.replyThumbMediaId]
        replyThumbMediaUrl = row[MpMenuTable.replyThumbMediaUrl]
        replyArticles = row[MpMenuTable.replyArticles]
        replyMusicUrl = row[MpMenuTable.replyMusicUrl]
        replyHqMusicUrl = row[MpMenuTable.replyHqMusicUrl]
        creator = row[MpMenuTable.creator]
        createTime = row[MpMenuTable.createTime]
        updater = row[MpMenuTable.updater]
        updateTime = row[MpMenuTable.updateTime]
        deleted = row[MpMenuTable.deleted]
        tenantId = row[MpMenuTable.tenantId]
    }
}
