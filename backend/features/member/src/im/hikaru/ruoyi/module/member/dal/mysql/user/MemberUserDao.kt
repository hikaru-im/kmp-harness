package im.hikaru.ruoyi.module.member.dal.mysql.user

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserPageReqVO
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberUserDao {
    fun selectById(id: Long): MemberUserDO? = transaction { MemberUserTable.selectAll().where { conditions(MemberUserTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberUserDO> = if (ids.isEmpty()) emptyList() else transaction { MemberUserTable.selectAll().where { conditions(MemberUserTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberUserDO> = transaction { MemberUserTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberUserTable.selectAll().where { conditions() }.count() }
    fun selectByMobile(mobile: String): MemberUserDO? = transaction {
        MemberUserTable.selectAll().where { conditions(MemberUserTable.mobile eq mobile) }.singleOrNull()?.let(::toEntity)
    }
    fun selectByEmail(email: String): MemberUserDO? = transaction {
        MemberUserTable.selectAll().where { conditions(MemberUserTable.email eq email) }.singleOrNull()?.let(::toEntity)
    }
    fun selectListByNicknameLike(nickname: String): List<MemberUserDO> = transaction {
        MemberUserTable.selectAll().where { conditions(MemberUserTable.nickname like "%$nickname%") }.map(::toEntity)
    }
    fun selectPage(reqVO: MemberUserPageReqVO): PageResult<MemberUserDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.mobile?.takeIf { it.isNotBlank() }?.let { ops += MemberUserTable.mobile like "%$it%" }
        reqVO.email?.takeIf { it.isNotBlank() }?.let { ops += MemberUserTable.email like "%$it%" }
        reqVO.nickname?.takeIf { it.isNotBlank() }?.let { ops += MemberUserTable.nickname like "%$it%" }
        reqVO.loginDate?.takeIf { it.size >= 2 }?.let {
            ops += MemberUserTable.loginDate greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberUserTable.loginDate lessEq it[1].toKotlinLocalDateTime()
        }
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MemberUserTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberUserTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        reqVO.levelId?.let { ops += MemberUserTable.levelId eq it }
        reqVO.groupId?.let { ops += MemberUserTable.groupId eq it }
        val users = MemberUserTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberUserTable.id, SortOrder.DESC)
            .map(::toEntity)
            .let { list -> reqVO.tagIds?.takeIf { it.isNotEmpty() }?.let { tags -> list.filter { user -> user.tagIds.orEmpty().any(tags::contains) } } ?: list }
        val total = users.size.toLong()
        val page = if (reqVO.pageSize == PageParam.PAGE_SIZE_NONE) users else {
            val from = ((reqVO.pageNo - 1) * reqVO.pageSize).coerceAtLeast(0)
            if (from >= users.size) emptyList() else users.subList(from, minOf(from + reqVO.pageSize, users.size))
        }
        PageResult(total = total, list = page)
    }
    fun selectCountByGroupId(groupId: Long): Long = transaction {
        MemberUserTable.selectAll().where { conditions(MemberUserTable.groupId eq groupId) }.count()
    }
    fun selectCountByLevelId(levelId: Long): Long = transaction {
        MemberUserTable.selectAll().where { conditions(MemberUserTable.levelId eq levelId) }.count()
    }
    fun selectCountByTagId(tagId: Long): Long = transaction {
        MemberUserTable.selectAll().where { conditions() }.map(::toEntity).count { tagId in it.tagIds.orEmpty() }.toLong()
    }
    fun updatePoint(id: Long, delta: Int): Int {
        require(delta != 0)
        return transaction {
            val ops = mutableListOf<Op<Boolean>>(MemberUserTable.id eq id)
            if (delta < 0) ops += MemberUserTable.point greaterEq -delta
            MemberUserTable.update(where = { conditions(*ops.toTypedArray()) }) {
                it[MemberUserTable.point] = coalesce(MemberUserTable.point, intLiteral(0)) + delta
            }
        }
    }
    fun insert(entity: MemberUserDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberUserTable.insert {
            it[MemberUserTable.mobile] = entity.mobile
            it[MemberUserTable.email] = entity.email
            it[MemberUserTable.password] = entity.password
            it[MemberUserTable.status] = entity.status
            it[MemberUserTable.registerIp] = entity.registerIp
            it[MemberUserTable.registerTerminal] = entity.registerTerminal
            it[MemberUserTable.loginIp] = entity.loginIp
            it[MemberUserTable.loginDate] = entity.loginDate
            it[MemberUserTable.nickname] = entity.nickname
            it[MemberUserTable.avatar] = entity.avatar
            it[MemberUserTable.profileVersion] = entity.profileVersion
            it[MemberUserTable.name] = entity.name
            it[MemberUserTable.sex] = entity.sex
            it[MemberUserTable.birthday] = entity.birthday
            it[MemberUserTable.areaId] = entity.areaId
            it[MemberUserTable.mark] = entity.mark
            it[MemberUserTable.point] = entity.point ?: 0
            it[MemberUserTable.tagIds] = entity.tagIds
            it[MemberUserTable.levelId] = entity.levelId
            it[MemberUserTable.experience] = entity.experience ?: 0
            it[MemberUserTable.groupId] = entity.groupId
            it[MemberUserTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberUserTable.creator] = entity.creator
            it[MemberUserTable.updater] = entity.updater
            it[MemberUserTable.createTime] = requireNotNull(entity.createTime)
            it[MemberUserTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberUserTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberUserDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberUserTable.update(where = { conditions(MemberUserTable.id eq id) }) {
            entity.mobile?.let { value -> it[MemberUserTable.mobile] = value }
            entity.password?.let { value -> it[MemberUserTable.password] = value }
            entity.status?.let { value -> it[MemberUserTable.status] = value }
            entity.registerIp?.let { value -> it[MemberUserTable.registerIp] = value }
            entity.registerTerminal?.let { value -> it[MemberUserTable.registerTerminal] = value }
            entity.loginIp?.let { value -> it[MemberUserTable.loginIp] = value }
            entity.loginDate?.let { value -> it[MemberUserTable.loginDate] = value }
            entity.name?.let { value -> it[MemberUserTable.name] = value }
            entity.birthday?.let { value -> it[MemberUserTable.birthday] = value }
            entity.areaId?.let { value -> it[MemberUserTable.areaId] = value }
            entity.mark?.let { value -> it[MemberUserTable.mark] = value }
            entity.point?.let { value -> it[MemberUserTable.point] = value }
            entity.tagIds?.let { value -> it[MemberUserTable.tagIds] = value }
            entity.levelId?.let { value -> it[MemberUserTable.levelId] = value }
            entity.experience?.let { value -> it[MemberUserTable.experience] = value }
            entity.groupId?.let { value -> it[MemberUserTable.groupId] = value }
            entity.updater?.let { value -> it[MemberUserTable.updater] = value }
            it[MemberUserTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun updateProfileByIdAndVersion(id: Long, entity: MemberUserDO, expectedVersion: Long): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { MemberUserTable.update(where = {
            conditions(
                MemberUserTable.id eq id,
                MemberUserTable.profileVersion eq expectedVersion,
            )
        }) {
            it[MemberUserTable.nickname] = entity.nickname
            it[MemberUserTable.avatar] = entity.avatar
            it[MemberUserTable.email] = entity.email
            it[MemberUserTable.sex] = entity.sex
            entity.updater?.let { value -> it[MemberUserTable.updater] = value }
            it[MemberUserTable.profileVersion] = expectedVersion + 1
            it[MemberUserTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberUserTable.update(where = { conditions(MemberUserTable.id eq id) }) { it[MemberUserTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberUserTable.update(where = { conditions(MemberUserTable.id inList ids) }) { it[MemberUserTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberUserTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberUserTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberUserDO().apply {
        id = row[MemberUserTable.id]
        mobile = row[MemberUserTable.mobile]
        email = row[MemberUserTable.email]
        password = row[MemberUserTable.password]
        status = row[MemberUserTable.status]
        registerIp = row[MemberUserTable.registerIp]
        registerTerminal = row[MemberUserTable.registerTerminal]
        loginIp = row[MemberUserTable.loginIp]
        loginDate = row[MemberUserTable.loginDate]
        nickname = row[MemberUserTable.nickname]
        avatar = row[MemberUserTable.avatar]
        profileVersion = row[MemberUserTable.profileVersion]
        name = row[MemberUserTable.name]
        sex = row[MemberUserTable.sex]
        birthday = row[MemberUserTable.birthday]
        areaId = row[MemberUserTable.areaId]
        mark = row[MemberUserTable.mark]
        point = row[MemberUserTable.point]
        tagIds = row[MemberUserTable.tagIds]
        levelId = row[MemberUserTable.levelId]
        experience = row[MemberUserTable.experience]
        groupId = row[MemberUserTable.groupId]
        creator = row[MemberUserTable.creator]
        createTime = row[MemberUserTable.createTime]
        updater = row[MemberUserTable.updater]
        updateTime = row[MemberUserTable.updateTime]
        deleted = row[MemberUserTable.deleted]
        tenantId = row[MemberUserTable.tenantId]
    }
}
