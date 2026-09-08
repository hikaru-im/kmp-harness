package im.hikaru.ruoyi.module.mp.dal.mysql.tag

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagPageReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.tag.MpTagDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpTagDao {
    fun selectById(id: Long): MpTagDO? = transaction { MpTagTable.selectAll().where { conditions(MpTagTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpTagDO> = if (ids.isEmpty()) emptyList() else transaction { MpTagTable.selectAll().where { conditions(MpTagTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpTagDO> = transaction { MpTagTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpTagTable.selectAll().where { conditions() }.count() }
    fun selectListByAccountId(accountId: Long): List<MpTagDO> = transaction {
        MpTagTable.selectAll().where { conditions(MpTagTable.accountId eq accountId) }
            .orderBy(MpTagTable.id, SortOrder.DESC).map(::toEntity)
    }
    fun selectPage(reqVO: MpTagPageReqVO): PageResult<MpTagDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.accountId?.let { ops += MpTagTable.accountId eq it }
        reqVO.name?.takeIf(String::isNotBlank)?.let { ops += MpTagTable.name like "%$it%" }
        val rows = MpTagTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MpTagTable.id, SortOrder.DESC).map(::toEntity)
        rows.toPage(reqVO)
    }
    fun insert(entity: MpTagDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpTagTable.insert {
            it[MpTagTable.tagId] = entity.tagId
            it[MpTagTable.name] = entity.name
            it[MpTagTable.count] = entity.count
            it[MpTagTable.accountId] = entity.accountId
            it[MpTagTable.appId] = entity.appId
            it[MpTagTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpTagTable.creator] = entity.creator
            it[MpTagTable.updater] = entity.updater
            it[MpTagTable.createTime] = requireNotNull(entity.createTime)
            it[MpTagTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpTagTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpTagDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpTagTable.update(where = { conditions(MpTagTable.id eq id) }) {
            entity.tagId?.let { value -> it[MpTagTable.tagId] = value }
            entity.name?.let { value -> it[MpTagTable.name] = value }
            entity.count?.let { value -> it[MpTagTable.count] = value }
            entity.accountId?.let { value -> it[MpTagTable.accountId] = value }
            entity.appId?.let { value -> it[MpTagTable.appId] = value }
            entity.updater?.let { value -> it[MpTagTable.updater] = value }
            it[MpTagTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpTagTable.update(where = { conditions(MpTagTable.id eq id) }) { it[MpTagTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpTagTable.update(where = { conditions(MpTagTable.id inList ids) }) { it[MpTagTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpTagTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpTagTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpTagDO().apply {
        id = row[MpTagTable.id]
        tagId = row[MpTagTable.tagId]
        name = row[MpTagTable.name]
        count = row[MpTagTable.count]
        accountId = row[MpTagTable.accountId]
        appId = row[MpTagTable.appId]
        creator = row[MpTagTable.creator]
        createTime = row[MpTagTable.createTime]
        updater = row[MpTagTable.updater]
        updateTime = row[MpTagTable.updateTime]
        deleted = row[MpTagTable.deleted]
        tenantId = row[MpTagTable.tenantId]
    }

    private fun <T> List<T>.toPage(page: PageParam): PageResult<T> {
        val total = size.toLong()
        if (page.pageSize == PageParam.PAGE_SIZE_NONE) return PageResult(total, this)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        val list = if (from >= size) emptyList() else subList(from, minOf(from + page.pageSize, size))
        return PageResult(total, list)
    }
}
