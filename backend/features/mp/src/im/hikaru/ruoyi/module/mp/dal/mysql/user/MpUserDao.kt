package im.hikaru.ruoyi.module.mp.dal.mysql.user

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserPageReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.user.MpUserDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpUserDao {
    fun selectById(id: Long): MpUserDO? = transaction { MpUserTable.selectAll().where { conditions(MpUserTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpUserDO> = if (ids.isEmpty()) emptyList() else transaction { MpUserTable.selectAll().where { conditions(MpUserTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpUserDO> = transaction { MpUserTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpUserTable.selectAll().where { conditions() }.count() }
    fun selectByAppIdAndOpenid(appId: String, openid: String): MpUserDO? = transaction {
        MpUserTable.selectAll().where { conditions(MpUserTable.appId eq appId, MpUserTable.openid eq openid) }
            .singleOrNull()?.let(::toEntity)
    }
    fun selectListByAppIdAndOpenid(appId: String, openids: Collection<String>): List<MpUserDO> =
        if (openids.isEmpty()) emptyList() else transaction {
            MpUserTable.selectAll().where { conditions(MpUserTable.appId eq appId, MpUserTable.openid inList openids) }
                .map(::toEntity)
        }
    fun selectPage(reqVO: MpUserPageReqVO): PageResult<MpUserDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.accountId?.let { ops += MpUserTable.accountId eq it }
        reqVO.openid?.takeIf(String::isNotBlank)?.let { ops += MpUserTable.openid like "%$it%" }
        reqVO.unionId?.takeIf(String::isNotBlank)?.let { ops += MpUserTable.unionId like "%$it%" }
        reqVO.nickname?.takeIf(String::isNotBlank)?.let { ops += MpUserTable.nickname like "%$it%" }
        val rows = MpUserTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MpUserTable.id, SortOrder.DESC).map(::toEntity)
        rows.toPage(reqVO)
    }
    fun insert(entity: MpUserDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpUserTable.insert {
            it[MpUserTable.openid] = entity.openid
            it[MpUserTable.unionId] = entity.unionId
            it[MpUserTable.subscribeStatus] = entity.subscribeStatus
            it[MpUserTable.subscribeTime] = entity.subscribeTime
            it[MpUserTable.unsubscribeTime] = entity.unsubscribeTime
            it[MpUserTable.nickname] = entity.nickname
            it[MpUserTable.headImageUrl] = entity.headImageUrl
            it[MpUserTable.language] = entity.language
            it[MpUserTable.country] = entity.country
            it[MpUserTable.province] = entity.province
            it[MpUserTable.city] = entity.city
            it[MpUserTable.remark] = entity.remark
            it[MpUserTable.tagIds] = entity.tagIds
            it[MpUserTable.accountId] = entity.accountId
            it[MpUserTable.appId] = entity.appId
            it[MpUserTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpUserTable.creator] = entity.creator
            it[MpUserTable.updater] = entity.updater
            it[MpUserTable.createTime] = requireNotNull(entity.createTime)
            it[MpUserTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpUserTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpUserDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpUserTable.update(where = { conditions(MpUserTable.id eq id) }) {
            entity.openid?.let { value -> it[MpUserTable.openid] = value }
            entity.unionId?.let { value -> it[MpUserTable.unionId] = value }
            entity.subscribeStatus?.let { value -> it[MpUserTable.subscribeStatus] = value }
            entity.subscribeTime?.let { value -> it[MpUserTable.subscribeTime] = value }
            entity.unsubscribeTime?.let { value -> it[MpUserTable.unsubscribeTime] = value }
            entity.nickname?.let { value -> it[MpUserTable.nickname] = value }
            entity.headImageUrl?.let { value -> it[MpUserTable.headImageUrl] = value }
            entity.language?.let { value -> it[MpUserTable.language] = value }
            entity.country?.let { value -> it[MpUserTable.country] = value }
            entity.province?.let { value -> it[MpUserTable.province] = value }
            entity.city?.let { value -> it[MpUserTable.city] = value }
            entity.remark?.let { value -> it[MpUserTable.remark] = value }
            entity.tagIds?.let { value -> it[MpUserTable.tagIds] = value }
            entity.accountId?.let { value -> it[MpUserTable.accountId] = value }
            entity.appId?.let { value -> it[MpUserTable.appId] = value }
            entity.updater?.let { value -> it[MpUserTable.updater] = value }
            it[MpUserTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpUserTable.update(where = { conditions(MpUserTable.id eq id) }) { it[MpUserTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpUserTable.update(where = { conditions(MpUserTable.id inList ids) }) { it[MpUserTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpUserTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpUserTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpUserDO().apply {
        id = row[MpUserTable.id]
        openid = row[MpUserTable.openid]
        unionId = row[MpUserTable.unionId]
        subscribeStatus = row[MpUserTable.subscribeStatus]
        subscribeTime = row[MpUserTable.subscribeTime]
        unsubscribeTime = row[MpUserTable.unsubscribeTime]
        nickname = row[MpUserTable.nickname]
        headImageUrl = row[MpUserTable.headImageUrl]
        language = row[MpUserTable.language]
        country = row[MpUserTable.country]
        province = row[MpUserTable.province]
        city = row[MpUserTable.city]
        remark = row[MpUserTable.remark]
        tagIds = row[MpUserTable.tagIds]
        accountId = row[MpUserTable.accountId]
        appId = row[MpUserTable.appId]
        creator = row[MpUserTable.creator]
        createTime = row[MpUserTable.createTime]
        updater = row[MpUserTable.updater]
        updateTime = row[MpUserTable.updateTime]
        deleted = row[MpUserTable.deleted]
        tenantId = row[MpUserTable.tenantId]
    }

    private fun <T> List<T>.toPage(page: PageParam): PageResult<T> {
        val total = size.toLong()
        if (page.pageSize == PageParam.PAGE_SIZE_NONE) return PageResult(total, this)
        val from = ((page.pageNo - 1) * page.pageSize).coerceAtLeast(0)
        val list = if (from >= size) emptyList() else subList(from, minOf(from + page.pageSize, size))
        return PageResult(total, list)
    }
}
