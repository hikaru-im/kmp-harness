package im.hikaru.ruoyi.module.system.dal.mysql.mail

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailAccountDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object MailAccountDao {
    fun selectById(id: Long): MailAccountDO? = transaction {
        MailAccountTable.selectAll().where { conditions(MailAccountTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectList(): List<MailAccountDO> = transaction {
        MailAccountTable.selectAll().where { conditions() }.orderBy(MailAccountTable.id, SortOrder.DESC).map(::toEntity)
    }

    fun selectPage(req: MailAccountPageReqVO): PageResult<MailAccountDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.mail?.takeIf { it.isNotBlank() }?.let { ops += MailAccountTable.mail like "%$it%" }
        req.username?.takeIf { it.isNotBlank() }?.let { ops += MailAccountTable.username like "%$it%" }
        MailAccountTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MailAccountTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: MailAccountDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            MailAccountTable.insert {
                it[mail] = requireNotNull(entity.mail)
                it[username] = requireNotNull(entity.username)
                it[password] = requireNotNull(entity.password)
                it[host] = requireNotNull(entity.host)
                it[port] = requireNotNull(entity.port)
                it[sslEnable] = requireNotNull(entity.sslEnable)
                it[starttlsEnable] = requireNotNull(entity.starttlsEnable)
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(MailAccountTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: MailAccountDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            MailAccountTable.update(where = { conditions(MailAccountTable.id eq requireNotNull(entity.id)) }) {
                it[mail] = requireNotNull(entity.mail)
                it[username] = requireNotNull(entity.username)
                it[password] = requireNotNull(entity.password)
                it[host] = requireNotNull(entity.host)
                it[port] = requireNotNull(entity.port)
                it[sslEnable] = requireNotNull(entity.sslEnable)
                it[starttlsEnable] = requireNotNull(entity.starttlsEnable)
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        MailAccountTable.update(where = { conditions(MailAccountTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        MailAccountTable.update(where = { conditions(MailAccountTable.id inList ids) }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(MailAccountTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow) = MailAccountDO().apply {
        id = row[MailAccountTable.id]
        mail = row[MailAccountTable.mail]
        username = row[MailAccountTable.username]
        password = row[MailAccountTable.password]
        host = row[MailAccountTable.host]
        port = row[MailAccountTable.port]
        sslEnable = row[MailAccountTable.sslEnable]
        starttlsEnable = row[MailAccountTable.starttlsEnable]
        creator = row[MailAccountTable.creator]
        createTime = row[MailAccountTable.createTime]
        updater = row[MailAccountTable.updater]
        updateTime = row[MailAccountTable.updateTime]
        deleted = row[MailAccountTable.deleted]
    }
}
