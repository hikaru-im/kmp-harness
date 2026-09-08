package im.hikaru.ruoyi.module.system.dal.mysql.tenant

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantDO
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object TenantDao {
    fun selectById(id: Long): TenantDO? = transaction { TenantTable.selectAll().where { conditions(TenantTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByName(name: String): TenantDO? = transaction { TenantTable.selectAll().where { conditions(TenantTable.name eq name) }.singleOrNull()?.let(::toEntity) }
    fun selectList(): List<TenantDO> = transaction { TenantTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectListByWebsite(website: String): List<TenantDO> = selectList().filter { website in it.websites.orEmpty() }
    fun selectCountByPackageId(packageId: Long): Long = transaction { TenantTable.selectAll().where { conditions(TenantTable.packageId eq packageId) }.count() }
    fun selectListByPackageId(packageId: Long): List<TenantDO> = transaction { TenantTable.selectAll().where { conditions(TenantTable.packageId eq packageId) }.map(::toEntity) }
    fun selectListByStatus(status: Int): List<TenantDO> = transaction { TenantTable.selectAll().where { conditions(TenantTable.status eq status) }.map(::toEntity) }
    fun selectPage(req: TenantPageReqVO): PageResult<TenantDO> = transaction { val ops = mutableListOf<Op<Boolean>>(); req.name?.takeIf { it.isNotBlank() }?.let { ops += TenantTable.name like "%$it%" }; req.contactName?.takeIf { it.isNotBlank() }?.let { ops += TenantTable.contactName like "%$it%" }; req.contactMobile?.takeIf { it.isNotBlank() }?.let { ops += TenantTable.contactMobile like "%$it%" }; req.status?.let { ops += TenantTable.status eq it }; req.createTime?.getOrNull(0)?.let { ops += TenantTable.createTime greaterEq it }; req.createTime?.getOrNull(1)?.let { ops += TenantTable.createTime lessEq it }; TenantTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(TenantTable.id, SortOrder.DESC).toPageResult(req, ::toEntity) }
    fun insert(entity: TenantDO): Long { DefaultDBFieldHandler.fillOnInsert(entity); val id = transaction { TenantTable.insert {
        it[TenantTable.name] = requireNotNull(entity.name); it[TenantTable.contactUserId] = entity.contactUserId; it[TenantTable.contactName] = requireNotNull(entity.contactName); it[TenantTable.contactMobile] = entity.contactMobile
        it[TenantTable.status] = entity.status ?: 0; it[TenantTable.websites] = entity.websites; it[TenantTable.packageId] = requireNotNull(entity.packageId); it[TenantTable.expireTime] = requireNotNull(entity.expireTime); it[TenantTable.accountCount] = requireNotNull(entity.accountCount)
        it[TenantTable.creator] = entity.creator.orEmpty(); it[TenantTable.updater] = entity.updater.orEmpty(); it[TenantTable.createTime] = requireNotNull(entity.createTime); it[TenantTable.updateTime] = requireNotNull(entity.updateTime)
    }.get(TenantTable.id) }; entity.id = id; return id }
    fun updateById(entity: TenantDO) { DefaultDBFieldHandler.fillOnUpdate(entity); val id = requireNotNull(entity.id); transaction { TenantTable.update(where = { conditions(TenantTable.id eq id) }) {
        entity.name?.let { v -> it[TenantTable.name] = v }; entity.contactUserId?.let { v -> it[TenantTable.contactUserId] = v }; entity.contactName?.let { v -> it[TenantTable.contactName] = v }; entity.contactMobile?.let { v -> it[TenantTable.contactMobile] = v }
        entity.status?.let { v -> it[TenantTable.status] = v }; entity.websites?.let { v -> it[TenantTable.websites] = v }; entity.packageId?.let { v -> it[TenantTable.packageId] = v }; entity.expireTime?.let { v -> it[TenantTable.expireTime] = v }; entity.accountCount?.let { v -> it[TenantTable.accountCount] = v }; entity.updater?.let { v -> it[TenantTable.updater] = v }; it[TenantTable.updateTime] = requireNotNull(entity.updateTime)
    } } }
    fun deleteById(id: Long): Int = transaction { TenantTable.update(where = { conditions(TenantTable.id eq id) }) { it[TenantTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>) = listOf(TenantTable.deleted eq false, *extra).compoundAnd()
    private fun toEntity(row: ResultRow) = TenantDO().apply { id = row[TenantTable.id]; name = row[TenantTable.name]; contactUserId = row[TenantTable.contactUserId]; contactName = row[TenantTable.contactName]; contactMobile = row[TenantTable.contactMobile]; status = row[TenantTable.status]; websites = row[TenantTable.websites]; packageId = row[TenantTable.packageId]; expireTime = row[TenantTable.expireTime]; accountCount = row[TenantTable.accountCount]; creator = row[TenantTable.creator]; createTime = row[TenantTable.createTime]; updater = row[TenantTable.updater]; updateTime = row[TenantTable.updateTime]; deleted = row[TenantTable.deleted] }
}
