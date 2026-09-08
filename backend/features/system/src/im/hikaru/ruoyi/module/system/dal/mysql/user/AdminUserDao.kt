package im.hikaru.ruoyi.module.system.dal.mysql.user

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
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

object AdminUserDao {
    fun selectById(id: Long): AdminUserDO? = transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<AdminUserDO> = if (ids.isEmpty()) emptyList() else transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.id inList ids) }.map(::toEntity) }
    fun selectByUsername(username: String): AdminUserDO? = transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.username eq username) }.singleOrNull()?.let(::toEntity) }
    fun selectByEmail(email: String): AdminUserDO? = transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.email eq email) }.singleOrNull()?.let(::toEntity) }
    fun selectByMobile(mobile: String): AdminUserDO? = transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.mobile eq mobile) }.singleOrNull()?.let(::toEntity) }
    fun selectListByNickname(nickname: String): List<AdminUserDO> = transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.nickname like "%$nickname%") }.map(::toEntity) }
    fun selectListByStatus(status: Int): List<AdminUserDO> = transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.status eq status) }.map(::toEntity) }
    fun selectListByDeptIds(deptIds: Collection<Long>): List<AdminUserDO> = if (deptIds.isEmpty()) emptyList() else transaction { AdminUserTable.selectAll().where { conditions(AdminUserTable.deptId inList deptIds) }.map(::toEntity) }
    fun selectCount(): Long = transaction { AdminUserTable.selectAll().where { conditions() }.count() }
    fun selectPage(req: UserPageReqVO, deptIds: Collection<Long>? = null, userIds: Collection<Long>? = null): PageResult<AdminUserDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.username?.takeIf { it.isNotBlank() }?.let { ops += AdminUserTable.username like "%$it%" }
        req.mobile?.takeIf { it.isNotBlank() }?.let { ops += AdminUserTable.mobile like "%$it%" }
        req.status?.let { ops += AdminUserTable.status eq it }
        req.createTime?.getOrNull(0)?.let { ops += AdminUserTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += AdminUserTable.createTime lessEq it }
        if (!deptIds.isNullOrEmpty()) ops += AdminUserTable.deptId inList deptIds
        if (!userIds.isNullOrEmpty()) ops += AdminUserTable.id inList userIds
        AdminUserTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(AdminUserTable.id, SortOrder.DESC).toPageResult(req, ::toEntity)
    }
    fun insert(entity: AdminUserDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { AdminUserTable.insert {
            it[AdminUserTable.username] = requireNotNull(entity.username)
            it[AdminUserTable.password] = entity.password.orEmpty()
            it[AdminUserTable.nickname] = requireNotNull(entity.nickname)
            it[AdminUserTable.remark] = entity.remark
            it[AdminUserTable.deptId] = entity.deptId
            it[AdminUserTable.postIds] = entity.postIds
            it[AdminUserTable.email] = entity.email
            it[AdminUserTable.mobile] = entity.mobile
            it[AdminUserTable.sex] = entity.sex
            it[AdminUserTable.avatar] = entity.avatar
            it[AdminUserTable.status] = entity.status ?: 0
            it[AdminUserTable.loginIp] = entity.loginIp
            it[AdminUserTable.loginDate] = entity.loginDate
            it[AdminUserTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[AdminUserTable.creator] = entity.creator.orEmpty(); it[AdminUserTable.updater] = entity.updater.orEmpty()
            it[AdminUserTable.createTime] = requireNotNull(entity.createTime); it[AdminUserTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(AdminUserTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: AdminUserDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        transaction { AdminUserTable.update(where = { conditions(AdminUserTable.id eq id) }) {
            entity.username?.let { value -> it[AdminUserTable.username] = value }
            entity.password?.let { value -> it[AdminUserTable.password] = value }
            entity.nickname?.let { value -> it[AdminUserTable.nickname] = value }
            entity.remark?.let { value -> it[AdminUserTable.remark] = value }
            entity.deptId?.let { value -> it[AdminUserTable.deptId] = value }
            entity.postIds?.let { value -> it[AdminUserTable.postIds] = value }
            entity.email?.let { value -> it[AdminUserTable.email] = value }
            entity.mobile?.let { value -> it[AdminUserTable.mobile] = value }
            entity.sex?.let { value -> it[AdminUserTable.sex] = value }
            entity.avatar?.let { value -> it[AdminUserTable.avatar] = value }
            entity.status?.let { value -> it[AdminUserTable.status] = value }
            entity.loginIp?.let { value -> it[AdminUserTable.loginIp] = value }
            entity.loginDate?.let { value -> it[AdminUserTable.loginDate] = value }
            entity.updater?.let { value -> it[AdminUserTable.updater] = value }
            it[AdminUserTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { AdminUserTable.update(where = { conditions(AdminUserTable.id eq id) }) { it[AdminUserTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { AdminUserTable.update(where = { conditions(AdminUserTable.id inList ids) }) { it[AdminUserTable.deleted] = true } }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(AdminUserTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += AdminUserTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    private fun toEntity(row: ResultRow) = AdminUserDO().apply {
        id = row[AdminUserTable.id]; username = row[AdminUserTable.username]; password = row[AdminUserTable.password]; nickname = row[AdminUserTable.nickname]
        remark = row[AdminUserTable.remark]; deptId = row[AdminUserTable.deptId]; postIds = row[AdminUserTable.postIds]; email = row[AdminUserTable.email]; mobile = row[AdminUserTable.mobile]
        sex = row[AdminUserTable.sex]; avatar = row[AdminUserTable.avatar]; status = row[AdminUserTable.status]; loginIp = row[AdminUserTable.loginIp]; loginDate = row[AdminUserTable.loginDate]
        creator = row[AdminUserTable.creator]; createTime = row[AdminUserTable.createTime]; updater = row[AdminUserTable.updater]; updateTime = row[AdminUserTable.updateTime]; deleted = row[AdminUserTable.deleted]; tenantId = row[AdminUserTable.tenantId]
    }
}
