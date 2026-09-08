package im.hikaru.ruoyi.module.mp.dal.mysql.account

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountPageReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpAccountDao {
    fun selectById(id: Long): MpAccountDO? = transaction { MpAccountTable.selectAll().where { conditions(MpAccountTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpAccountDO> = if (ids.isEmpty()) emptyList() else transaction { MpAccountTable.selectAll().where { conditions(MpAccountTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpAccountDO> = transaction { MpAccountTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpAccountTable.selectAll().where { conditions() }.count() }
    fun selectByAppId(appId: String): MpAccountDO? = transaction {
        MpAccountTable.selectAll().where { conditions(MpAccountTable.appId eq appId) }.singleOrNull()?.let(::toEntity)
    }
    fun selectPage(reqVO: MpAccountPageReqVO): PageResult<MpAccountDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.name?.takeIf(String::isNotBlank)?.let { ops += MpAccountTable.name like "%$it%" }
        reqVO.account?.takeIf(String::isNotBlank)?.let { ops += MpAccountTable.account like "%$it%" }
        reqVO.appId?.takeIf(String::isNotBlank)?.let { ops += MpAccountTable.appId like "%$it%" }
        val rows = MpAccountTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MpAccountTable.id, SortOrder.DESC).map(::toEntity)
        rows.toPage(reqVO)
    }
    fun selectCountByUpdateTimeGt(maxUpdateTime: LocalDateTime): Long = transaction {
        MpAccountTable.selectAll().where { MpAccountTable.updateTime greater maxUpdateTime }.count()
    }
    fun insert(entity: MpAccountDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpAccountTable.insert {
            it[MpAccountTable.name] = entity.name
            it[MpAccountTable.account] = entity.account
            it[MpAccountTable.appId] = entity.appId
            it[MpAccountTable.url] = entity.url
            it[MpAccountTable.appSecret] = entity.appSecret
            it[MpAccountTable.token] = entity.token
            it[MpAccountTable.aesKey] = entity.aesKey
            it[MpAccountTable.qrCodeUrl] = entity.qrCodeUrl
            it[MpAccountTable.remark] = entity.remark
            it[MpAccountTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpAccountTable.creator] = entity.creator
            it[MpAccountTable.updater] = entity.updater
            it[MpAccountTable.createTime] = requireNotNull(entity.createTime)
            it[MpAccountTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpAccountTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpAccountDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpAccountTable.update(where = { conditions(MpAccountTable.id eq id) }) {
            entity.name?.let { value -> it[MpAccountTable.name] = value }
            entity.account?.let { value -> it[MpAccountTable.account] = value }
            entity.appId?.let { value -> it[MpAccountTable.appId] = value }
            entity.url?.let { value -> it[MpAccountTable.url] = value }
            entity.appSecret?.let { value -> it[MpAccountTable.appSecret] = value }
            entity.token?.let { value -> it[MpAccountTable.token] = value }
            entity.aesKey?.let { value -> it[MpAccountTable.aesKey] = value }
            entity.qrCodeUrl?.let { value -> it[MpAccountTable.qrCodeUrl] = value }
            entity.remark?.let { value -> it[MpAccountTable.remark] = value }
            entity.updater?.let { value -> it[MpAccountTable.updater] = value }
            it[MpAccountTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpAccountTable.update(where = { conditions(MpAccountTable.id eq id) }) { it[MpAccountTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpAccountTable.update(where = { conditions(MpAccountTable.id inList ids) }) { it[MpAccountTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpAccountTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpAccountTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpAccountDO().apply {
        id = row[MpAccountTable.id]
        name = row[MpAccountTable.name]
        account = row[MpAccountTable.account]
        appId = row[MpAccountTable.appId]
        url = row[MpAccountTable.url]
        appSecret = row[MpAccountTable.appSecret]
        token = row[MpAccountTable.token]
        aesKey = row[MpAccountTable.aesKey]
        qrCodeUrl = row[MpAccountTable.qrCodeUrl]
        remark = row[MpAccountTable.remark]
        creator = row[MpAccountTable.creator]
        createTime = row[MpAccountTable.createTime]
        updater = row[MpAccountTable.updater]
        updateTime = row[MpAccountTable.updateTime]
        deleted = row[MpAccountTable.deleted]
        tenantId = row[MpAccountTable.tenantId]
    }

    private fun <T> List<T>.toPage(page: PageParam): PageResult<T> {
        val total = size.toLong()
        if (page.pageSize == PageParam.PAGE_SIZE_NONE) return PageResult(total, this)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        val list = if (from >= size) emptyList() else subList(from, minOf(from + page.pageSize, size))
        return PageResult(total, list)
    }
}
