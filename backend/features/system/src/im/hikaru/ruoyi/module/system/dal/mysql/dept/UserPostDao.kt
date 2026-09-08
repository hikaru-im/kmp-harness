package im.hikaru.ruoyi.module.system.dal.mysql.dept

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.UserPostDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object UserPostDao {
    fun selectListByUserId(userId: Long): List<UserPostDO> = transaction { UserPostTable.selectAll().where { conditions(UserPostTable.userId eq userId) }.map(::toEntity) }
    fun selectListByPostIds(postIds: Collection<Long>): List<UserPostDO> = if (postIds.isEmpty()) emptyList() else transaction { UserPostTable.selectAll().where { conditions(UserPostTable.postId inList postIds) }.map(::toEntity) }
    fun deleteByUserIdAndPostId(userId: Long, postIds: Collection<Long>): Int = if (postIds.isEmpty()) 0 else transaction { UserPostTable.deleteWhere { conditions(UserPostTable.userId eq userId, UserPostTable.postId inList postIds) } }
    fun deleteByUserId(userId: Long): Int = transaction { UserPostTable.deleteWhere { conditions(UserPostTable.userId eq userId) } }
    fun insert(entity: UserPostDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { UserPostTable.insert {
            it[UserPostTable.userId] = requireNotNull(entity.userId); it[UserPostTable.postId] = requireNotNull(entity.postId); it[UserPostTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[UserPostTable.creator] = entity.creator.orEmpty(); it[UserPostTable.updater] = entity.updater.orEmpty(); it[UserPostTable.createTime] = requireNotNull(entity.createTime); it[UserPostTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(UserPostTable.id) }
        entity.id = id; return id
    }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(UserPostTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += UserPostTable.tenantId eq it }
        ops += extra; return ops.compoundAnd()
    }
    private fun toEntity(row: ResultRow) = UserPostDO().apply {
        id = row[UserPostTable.id]; userId = row[UserPostTable.userId]; postId = row[UserPostTable.postId]; creator = row[UserPostTable.creator]; createTime = row[UserPostTable.createTime]; updater = row[UserPostTable.updater]; updateTime = row[UserPostTable.updateTime]; deleted = row[UserPostTable.deleted]; tenantId = row[UserPostTable.tenantId]
    }
}
