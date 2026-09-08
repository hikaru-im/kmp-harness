package im.hikaru.ruoyi.module.system.dal.mysql.dict

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictDataDO
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

object DictDataDao {
    fun selectById(id: Long): DictDataDO? = transaction {
        DictDataTable.selectAll().where { conditions(DictDataTable.id eq id) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectByDictTypeAndValue(dictType: String, value: String): DictDataDO? = transaction {
        DictDataTable.selectAll().where {
            conditions(DictDataTable.dictType eq dictType, DictDataTable.value eq value)
        }.singleOrNull()?.let(::toEntity)
    }

    fun selectByDictTypeAndLabel(dictType: String, label: String): DictDataDO? = transaction {
        DictDataTable.selectAll().where {
            conditions(DictDataTable.dictType eq dictType, DictDataTable.label eq label)
        }.singleOrNull()?.let(::toEntity)
    }

    fun selectByDictTypeAndValues(dictType: String, values: Collection<String>): List<DictDataDO> {
        if (values.isEmpty()) return emptyList()
        return transaction {
            DictDataTable.selectAll().where {
                conditions(DictDataTable.dictType eq dictType, DictDataTable.value inList values)
            }.map(::toEntity)
        }
    }

    fun selectCountByDictType(dictType: String): Long = transaction {
        DictDataTable.selectAll().where { conditions(DictDataTable.dictType eq dictType) }.count()
    }

    fun selectPage(req: DictDataPageReqVO): PageResult<DictDataDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(DictDataTable.deleted eq false)
        req.label?.takeIf { it.isNotBlank() }?.let { ops += DictDataTable.label like "%$it%" }
        req.dictType?.takeIf { it.isNotBlank() }?.let { ops += DictDataTable.dictType eq it }
        req.status?.let { ops += DictDataTable.status eq it }
        DictDataTable.selectAll().where { ops.compoundAnd() }
            .orderBy(DictDataTable.dictType, SortOrder.DESC)
            .orderBy(DictDataTable.sort, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun selectListByStatusAndDictType(status: Int?, dictType: String?): List<DictDataDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(DictDataTable.deleted eq false)
        status?.let { ops += DictDataTable.status eq it }
        dictType?.let { ops += DictDataTable.dictType eq it }
        DictDataTable.selectAll().where { ops.compoundAnd() }.map(::toEntity)
    }

    fun selectListByDictType(dictType: String): List<DictDataDO> = transaction {
        DictDataTable.selectAll().where {
            conditions(DictDataTable.dictType eq dictType)
        }.orderBy(DictDataTable.sort, SortOrder.ASC).map(::toEntity)
    }

    fun insert(entity: DictDataDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val sort = requireNotNull(entity.sort)
        val label = requireNotNull(entity.label)
        val value = requireNotNull(entity.value)
        val dictType = requireNotNull(entity.dictType)
        val status = requireNotNull(entity.status)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            DictDataTable.insert {
                it[DictDataTable.sort] = sort
                it[DictDataTable.label] = label
                it[DictDataTable.value] = value
                it[DictDataTable.dictType] = dictType
                it[DictDataTable.status] = status
                it[DictDataTable.colorType] = entity.colorType
                it[DictDataTable.cssClass] = entity.cssClass
                it[DictDataTable.remark] = entity.remark
                it[DictDataTable.creator] = entity.creator.orEmpty()
                it[DictDataTable.updater] = entity.updater.orEmpty()
                it[DictDataTable.createTime] = createTime
                it[DictDataTable.updateTime] = updateTime
            }.get(DictDataTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: DictDataDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        val updateTime = requireNotNull(entity.updateTime)
        transaction {
            DictDataTable.update(where = { DictDataTable.id eq id }) {
                entity.sort?.let { value -> it[DictDataTable.sort] = value }
                entity.label?.let { value -> it[DictDataTable.label] = value }
                entity.value?.let { value -> it[DictDataTable.value] = value }
                entity.dictType?.let { value -> it[DictDataTable.dictType] = value }
                entity.status?.let { value -> it[DictDataTable.status] = value }
                it[DictDataTable.colorType] = entity.colorType
                it[DictDataTable.cssClass] = entity.cssClass
                it[DictDataTable.remark] = entity.remark
                entity.updater?.let { value -> it[DictDataTable.updater] = value }
                it[DictDataTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction { DictDataTable.deleteWhere { DictDataTable.id eq id } }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { DictDataTable.deleteWhere { DictDataTable.id inList ids } }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(DictDataTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow): DictDataDO = DictDataDO().apply {
        id = row[DictDataTable.id]
        sort = row[DictDataTable.sort]
        label = row[DictDataTable.label]
        value = row[DictDataTable.value]
        dictType = row[DictDataTable.dictType]
        status = row[DictDataTable.status]
        colorType = row[DictDataTable.colorType]
        cssClass = row[DictDataTable.cssClass]
        remark = row[DictDataTable.remark]
        creator = row[DictDataTable.creator]
        createTime = row[DictDataTable.createTime]
        updater = row[DictDataTable.updater]
        updateTime = row[DictDataTable.updateTime]
        deleted = row[DictDataTable.deleted]
    }
}
