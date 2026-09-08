package im.hikaru.ruoyi.module.system.dal.mysql.dept

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post.PostPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.PostDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object PostDao {
    fun selectById(id: Long): PostDO? = transaction { PostTable.selectAll().where { conditions(PostTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<PostDO> = if (ids.isEmpty()) emptyList() else transaction { PostTable.selectAll().where { conditions(PostTable.id inList ids) }.map(::toEntity) }
    fun selectByName(name: String): PostDO? = transaction { PostTable.selectAll().where { conditions(PostTable.name eq name) }.singleOrNull()?.let(::toEntity) }
    fun selectByCode(code: String): PostDO? = transaction { PostTable.selectAll().where { conditions(PostTable.code eq code) }.singleOrNull()?.let(::toEntity) }
    fun selectList(ids: Collection<Long>?, statuses: Collection<Int>?): List<PostDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        if (!ids.isNullOrEmpty()) ops += PostTable.id inList ids
        if (!statuses.isNullOrEmpty()) ops += PostTable.status inList statuses
        PostTable.selectAll().where { conditions(*ops.toTypedArray()) }.map(::toEntity)
    }
    fun selectPage(req: PostPageReqVO): PageResult<PostDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.code?.takeIf { it.isNotBlank() }?.let { ops += PostTable.code like "%$it%" }
        req.name?.takeIf { it.isNotBlank() }?.let { ops += PostTable.name like "%$it%" }
        req.status?.let { ops += PostTable.status eq it }
        PostTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PostTable.id, SortOrder.DESC).toPageResult(req, ::toEntity)
    }
    fun insert(entity: PostDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PostTable.insert {
            it[PostTable.code] = requireNotNull(entity.code); it[PostTable.name] = requireNotNull(entity.name); it[PostTable.sort] = requireNotNull(entity.sort)
            it[PostTable.status] = requireNotNull(entity.status); it[PostTable.remark] = entity.remark
            it[PostTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PostTable.creator] = entity.creator.orEmpty(); it[PostTable.updater] = entity.updater.orEmpty(); it[PostTable.createTime] = requireNotNull(entity.createTime); it[PostTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PostTable.id) }
        entity.id = id; return id
    }
    fun updateById(entity: PostDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        transaction { PostTable.update(where = { conditions(PostTable.id eq id) }) {
            entity.code?.let { value -> it[PostTable.code] = value }; entity.name?.let { value -> it[PostTable.name] = value }; entity.sort?.let { value -> it[PostTable.sort] = value }
            entity.status?.let { value -> it[PostTable.status] = value }; it[PostTable.remark] = entity.remark; entity.updater?.let { value -> it[PostTable.updater] = value }; it[PostTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PostTable.update(where = { conditions(PostTable.id eq id) }) { it[PostTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PostTable.update(where = { conditions(PostTable.id inList ids) }) { it[PostTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PostTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PostTable.tenantId eq it }
        ops += extra; return ops.compoundAnd()
    }
    private fun toEntity(row: ResultRow) = PostDO().apply {
        id = row[PostTable.id]; code = row[PostTable.code]; name = row[PostTable.name]; sort = row[PostTable.sort]; status = row[PostTable.status]; remark = row[PostTable.remark]
        creator = row[PostTable.creator]; createTime = row[PostTable.createTime]; updater = row[PostTable.updater]; updateTime = row[PostTable.updateTime]; deleted = row[PostTable.deleted]; tenantId = row[PostTable.tenantId]
    }
}
