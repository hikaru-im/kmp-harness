package im.hikaru.ruoyi.module.mp.dal.mysql.message

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateListReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageTemplateDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MpMessageTemplateDao {
    fun selectById(id: Long): MpMessageTemplateDO? = transaction { MpMessageTemplateTable.selectAll().where { conditions(MpMessageTemplateTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MpMessageTemplateDO> = if (ids.isEmpty()) emptyList() else transaction { MpMessageTemplateTable.selectAll().where { conditions(MpMessageTemplateTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MpMessageTemplateDO> = transaction { MpMessageTemplateTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MpMessageTemplateTable.selectAll().where { conditions() }.count() }
    fun selectList(reqVO: MpMessageTemplateListReqVO): List<MpMessageTemplateDO> = transaction {
        val accountId = requireNotNull(reqVO.accountId)
        MpMessageTemplateTable.selectAll().where { conditions(MpMessageTemplateTable.accountId eq accountId) }.map(::toEntity)
    }
    fun selectListByAppId(appId: String): List<MpMessageTemplateDO> = transaction {
        MpMessageTemplateTable.selectAll().where { conditions(MpMessageTemplateTable.appId eq appId) }.map(::toEntity)
    }
    fun insert(entity: MpMessageTemplateDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MpMessageTemplateTable.insert {
            it[MpMessageTemplateTable.accountId] = entity.accountId
            it[MpMessageTemplateTable.appId] = entity.appId
            it[MpMessageTemplateTable.templateId] = entity.templateId
            it[MpMessageTemplateTable.title] = entity.title
            it[MpMessageTemplateTable.content] = entity.content
            it[MpMessageTemplateTable.example] = entity.example
            it[MpMessageTemplateTable.primaryIndustry] = entity.primaryIndustry
            it[MpMessageTemplateTable.deputyIndustry] = entity.deputyIndustry
            it[MpMessageTemplateTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MpMessageTemplateTable.creator] = entity.creator
            it[MpMessageTemplateTable.updater] = entity.updater
            it[MpMessageTemplateTable.createTime] = requireNotNull(entity.createTime)
            it[MpMessageTemplateTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MpMessageTemplateTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MpMessageTemplateDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MpMessageTemplateTable.update(where = { conditions(MpMessageTemplateTable.id eq id) }) {
            entity.accountId?.let { value -> it[MpMessageTemplateTable.accountId] = value }
            entity.appId?.let { value -> it[MpMessageTemplateTable.appId] = value }
            entity.templateId?.let { value -> it[MpMessageTemplateTable.templateId] = value }
            entity.title?.let { value -> it[MpMessageTemplateTable.title] = value }
            entity.content?.let { value -> it[MpMessageTemplateTable.content] = value }
            entity.example?.let { value -> it[MpMessageTemplateTable.example] = value }
            entity.primaryIndustry?.let { value -> it[MpMessageTemplateTable.primaryIndustry] = value }
            entity.deputyIndustry?.let { value -> it[MpMessageTemplateTable.deputyIndustry] = value }
            entity.updater?.let { value -> it[MpMessageTemplateTable.updater] = value }
            it[MpMessageTemplateTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MpMessageTemplateTable.update(where = { conditions(MpMessageTemplateTable.id eq id) }) { it[MpMessageTemplateTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MpMessageTemplateTable.update(where = { conditions(MpMessageTemplateTable.id inList ids) }) { it[MpMessageTemplateTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MpMessageTemplateTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MpMessageTemplateTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MpMessageTemplateDO().apply {
        id = row[MpMessageTemplateTable.id]
        accountId = row[MpMessageTemplateTable.accountId]
        appId = row[MpMessageTemplateTable.appId]
        templateId = row[MpMessageTemplateTable.templateId]
        title = row[MpMessageTemplateTable.title]
        content = row[MpMessageTemplateTable.content]
        example = row[MpMessageTemplateTable.example]
        primaryIndustry = row[MpMessageTemplateTable.primaryIndustry]
        deputyIndustry = row[MpMessageTemplateTable.deputyIndustry]
        creator = row[MpMessageTemplateTable.creator]
        createTime = row[MpMessageTemplateTable.createTime]
        updater = row[MpMessageTemplateTable.updater]
        updateTime = row[MpMessageTemplateTable.updateTime]
        deleted = row[MpMessageTemplateTable.deleted]
        tenantId = row[MpMessageTemplateTable.tenantId]
    }
}
