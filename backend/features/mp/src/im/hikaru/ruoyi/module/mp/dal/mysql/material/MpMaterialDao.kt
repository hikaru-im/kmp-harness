package im.hikaru.ruoyi.module.mp.dal.mysql.material

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialPageReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.material.MpMaterialDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpMaterialDao {
    fun selectById(id: Long): MpMaterialDO? = transaction { MpMaterialTable.selectAll().where { conditions(MpMaterialTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpMaterialDO> = if (ids.isEmpty()) emptyList() else transaction { MpMaterialTable.selectAll().where { conditions(MpMaterialTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpMaterialDO> = transaction { MpMaterialTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpMaterialTable.selectAll().where { conditions() }.count() }
    fun selectByAccountIdAndMediaId(accountId: Long, mediaId: String): MpMaterialDO? = transaction {
        MpMaterialTable.selectAll().where { conditions(MpMaterialTable.accountId eq accountId, MpMaterialTable.mediaId eq mediaId) }
            .singleOrNull()?.let(::toEntity)
    }
    fun selectListByMediaId(mediaIds: Collection<String>): List<MpMaterialDO> =
        if (mediaIds.isEmpty()) emptyList() else transaction {
            MpMaterialTable.selectAll().where { conditions(MpMaterialTable.mediaId inList mediaIds) }.map(::toEntity)
        }
    fun selectPage(reqVO: MpMaterialPageReqVO): PageResult<MpMaterialDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.accountId?.let { ops += MpMaterialTable.accountId eq it }
        reqVO.permanent?.let { ops += MpMaterialTable.permanent eq it }
        reqVO.type?.takeIf(String::isNotBlank)?.let { ops += MpMaterialTable.type eq it }
        val rows = MpMaterialTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MpMaterialTable.id, SortOrder.DESC).map(::toEntity)
        rows.toPage(reqVO)
    }
    fun insert(entity: MpMaterialDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpMaterialTable.insert {
            it[MpMaterialTable.accountId] = entity.accountId
            it[MpMaterialTable.appId] = entity.appId
            it[MpMaterialTable.mediaId] = entity.mediaId
            it[MpMaterialTable.type] = entity.type
            it[MpMaterialTable.permanent] = entity.permanent
            it[MpMaterialTable.url] = entity.url
            it[MpMaterialTable.name] = entity.name
            it[MpMaterialTable.mpUrl] = entity.mpUrl
            it[MpMaterialTable.title] = entity.title
            it[MpMaterialTable.introduction] = entity.introduction
            it[MpMaterialTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpMaterialTable.creator] = entity.creator
            it[MpMaterialTable.updater] = entity.updater
            it[MpMaterialTable.createTime] = requireNotNull(entity.createTime)
            it[MpMaterialTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpMaterialTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpMaterialDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpMaterialTable.update(where = { conditions(MpMaterialTable.id eq id) }) {
            entity.accountId?.let { value -> it[MpMaterialTable.accountId] = value }
            entity.appId?.let { value -> it[MpMaterialTable.appId] = value }
            entity.mediaId?.let { value -> it[MpMaterialTable.mediaId] = value }
            entity.type?.let { value -> it[MpMaterialTable.type] = value }
            entity.permanent?.let { value -> it[MpMaterialTable.permanent] = value }
            entity.url?.let { value -> it[MpMaterialTable.url] = value }
            entity.name?.let { value -> it[MpMaterialTable.name] = value }
            entity.mpUrl?.let { value -> it[MpMaterialTable.mpUrl] = value }
            entity.title?.let { value -> it[MpMaterialTable.title] = value }
            entity.introduction?.let { value -> it[MpMaterialTable.introduction] = value }
            entity.updater?.let { value -> it[MpMaterialTable.updater] = value }
            it[MpMaterialTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpMaterialTable.update(where = { conditions(MpMaterialTable.id eq id) }) { it[MpMaterialTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpMaterialTable.update(where = { conditions(MpMaterialTable.id inList ids) }) { it[MpMaterialTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpMaterialTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpMaterialTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpMaterialDO().apply {
        id = row[MpMaterialTable.id]
        accountId = row[MpMaterialTable.accountId]
        appId = row[MpMaterialTable.appId]
        mediaId = row[MpMaterialTable.mediaId]
        type = row[MpMaterialTable.type]
        permanent = row[MpMaterialTable.permanent]
        url = row[MpMaterialTable.url]
        name = row[MpMaterialTable.name]
        mpUrl = row[MpMaterialTable.mpUrl]
        title = row[MpMaterialTable.title]
        introduction = row[MpMaterialTable.introduction]
        creator = row[MpMaterialTable.creator]
        createTime = row[MpMaterialTable.createTime]
        updater = row[MpMaterialTable.updater]
        updateTime = row[MpMaterialTable.updateTime]
        deleted = row[MpMaterialTable.deleted]
        tenantId = row[MpMaterialTable.tenantId]
    }

    private fun <T> List<T>.toPage(page: PageParam): PageResult<T> {
        val total = size.toLong()
        if (page.pageSize == PageParam.PAGE_SIZE_NONE) return PageResult(total, this)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        val list = if (from >= size) emptyList() else subList(from, minOf(from + page.pageSize, size))
        return PageResult(total, list)
    }
}
