package im.hikaru.ruoyi.module.pay.dal.mysql.transfer

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo.PayTransferPageReqVO
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.transfer.PayTransferDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayTransferDao {
    fun selectById(id: Long): PayTransferDO? = transaction { PayTransferTable.selectAll().where { conditions(PayTransferTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByNo(no: String): PayTransferDO? = transaction { PayTransferTable.selectAll().where { conditions(PayTransferTable.no eq no) }.singleOrNull()?.let(::toEntity) }
    fun selectByAppIdAndNo(appId: Long, no: String): PayTransferDO? = transaction { PayTransferTable.selectAll().where { conditions(PayTransferTable.appId eq appId, PayTransferTable.no eq no) }.singleOrNull()?.let(::toEntity) }
    fun selectByAppIdAndMerchantTransferId(appId: Long, merchantTransferId: String): PayTransferDO? = transaction {
        PayTransferTable.selectAll().where { conditions(PayTransferTable.appId eq appId, PayTransferTable.merchantTransferId eq merchantTransferId) }.singleOrNull()?.let(::toEntity)
    }
    fun selectByIds(ids: Collection<Long>): List<PayTransferDO> = if (ids.isEmpty()) emptyList() else transaction { PayTransferTable.selectAll().where { conditions(PayTransferTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayTransferDO> = transaction { PayTransferTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayTransferTable.selectAll().where { conditions() }.count() }
    fun selectListByStatus(status: Int): List<PayTransferDO> = transaction { PayTransferTable.selectAll().where { conditions(PayTransferTable.status eq status) }.map(::toEntity) }
    fun selectListByStatus(statuses: Collection<Int>): List<PayTransferDO> = if (statuses.isEmpty()) emptyList() else transaction {
        PayTransferTable.selectAll().where { conditions(PayTransferTable.status inList statuses) }.map(::toEntity)
    }
    fun selectPage(req: PayTransferPageReqVO): PageResult<PayTransferDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.no?.takeIf(String::isNotBlank)?.let { ops += PayTransferTable.no eq it }
        req.appId?.let { ops += PayTransferTable.appId eq it }
        req.channelCode?.takeIf(String::isNotBlank)?.let { ops += PayTransferTable.channelCode eq it }
        req.merchantOrderId?.takeIf(String::isNotBlank)?.let { ops += PayTransferTable.merchantTransferId like "%$it%" }
        req.userName?.takeIf(String::isNotBlank)?.let { ops += PayTransferTable.userName like "%$it%" }
        req.userAccount?.takeIf(String::isNotBlank)?.let { ops += PayTransferTable.userAccount like "%$it%" }
        req.channelTransferNo?.takeIf(String::isNotBlank)?.let { ops += PayTransferTable.channelTransferNo like "%$it%" }
        req.status?.let { ops += PayTransferTable.status eq it }
        req.createTime?.getOrNull(0)?.let { ops += PayTransferTable.createTime greaterEq it.toKotlinLocalDateTime() }
        req.createTime?.getOrNull(1)?.let { ops += PayTransferTable.createTime lessEq it.toKotlinLocalDateTime() }
        val all = PayTransferTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PayTransferTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong()
        if (req.pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((req.pageNo - 1) * req.pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + req.pageSize, all.size)))
    }
    fun updateByIdAndStatus(id: Long, status: Int, entity: PayTransferDO): Int {
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayTransferTable.update(where = { conditions(PayTransferTable.id eq id, PayTransferTable.status eq status) }) {
            entity.status?.let { value -> it[PayTransferTable.status] = value }
            entity.successTime?.let { value -> it[PayTransferTable.successTime] = value }
            entity.channelTransferNo?.let { value -> it[PayTransferTable.channelTransferNo] = value }
            entity.channelErrorCode?.let { value -> it[PayTransferTable.channelErrorCode] = value }
            entity.channelErrorMsg?.let { value -> it[PayTransferTable.channelErrorMsg] = value }
            entity.channelNotifyData?.let { value -> it[PayTransferTable.channelNotifyData] = value }
            entity.channelPackageInfo?.let { value -> it[PayTransferTable.channelPackageInfo] = value }
            entity.updater?.let { value -> it[PayTransferTable.updater] = value }
            it[PayTransferTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun updateByIdAndStatus(id: Long, statuses: Collection<Int>, entity: PayTransferDO): Int {
        if (statuses.isEmpty()) return 0
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayTransferTable.update(where = { conditions(PayTransferTable.id eq id, PayTransferTable.status inList statuses) }) {
            entity.status?.let { value -> it[PayTransferTable.status] = value }
            entity.successTime?.let { value -> it[PayTransferTable.successTime] = value }
            entity.channelTransferNo?.let { value -> it[PayTransferTable.channelTransferNo] = value }
            entity.channelErrorCode?.let { value -> it[PayTransferTable.channelErrorCode] = value }
            entity.channelErrorMsg?.let { value -> it[PayTransferTable.channelErrorMsg] = value }
            entity.channelNotifyData?.let { value -> it[PayTransferTable.channelNotifyData] = value }
            entity.channelPackageInfo?.let { value -> it[PayTransferTable.channelPackageInfo] = value }
            entity.updater?.let { value -> it[PayTransferTable.updater] = value }
            it[PayTransferTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun updateChannelPackageInfoIfAbsent(id: Long, channelPackageInfo: String): Int = transaction {
        val current = PayTransferTable.selectAll().where { conditions(PayTransferTable.id eq id) }
            .singleOrNull()?.get(PayTransferTable.channelPackageInfo)
        if (!current.isNullOrBlank()) return@transaction 0
        PayTransferTable.update(where = { conditions(PayTransferTable.id eq id) }) {
            it[PayTransferTable.channelPackageInfo] = channelPackageInfo
        }
    }
    fun insert(entity: PayTransferDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayTransferTable.insert {
            it[PayTransferTable.no] = entity.no
            it[PayTransferTable.appId] = entity.appId
            it[PayTransferTable.channelId] = entity.channelId
            it[PayTransferTable.channelCode] = entity.channelCode
            it[PayTransferTable.userId] = entity.userId
            it[PayTransferTable.userType] = entity.userType
            it[PayTransferTable.merchantTransferId] = entity.merchantTransferId
            it[PayTransferTable.subject] = entity.subject
            it[PayTransferTable.price] = entity.price
            it[PayTransferTable.userAccount] = entity.userAccount
            it[PayTransferTable.userName] = entity.userName
            it[PayTransferTable.status] = entity.status
            it[PayTransferTable.successTime] = entity.successTime
            it[PayTransferTable.notifyUrl] = entity.notifyUrl
            it[PayTransferTable.userIp] = entity.userIp
            it[PayTransferTable.channelExtras] = entity.channelExtras
            it[PayTransferTable.channelTransferNo] = entity.channelTransferNo
            it[PayTransferTable.channelErrorCode] = entity.channelErrorCode
            it[PayTransferTable.channelErrorMsg] = entity.channelErrorMsg
            it[PayTransferTable.channelNotifyData] = entity.channelNotifyData
            it[PayTransferTable.channelPackageInfo] = entity.channelPackageInfo
            it[PayTransferTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayTransferTable.creator] = entity.creator
            it[PayTransferTable.updater] = entity.updater
            it[PayTransferTable.createTime] = requireNotNull(entity.createTime)
            it[PayTransferTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayTransferTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayTransferDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayTransferTable.update(where = { conditions(PayTransferTable.id eq id) }) {
            entity.no?.let { value -> it[PayTransferTable.no] = value }
            entity.appId?.let { value -> it[PayTransferTable.appId] = value }
            entity.channelId?.let { value -> it[PayTransferTable.channelId] = value }
            entity.channelCode?.let { value -> it[PayTransferTable.channelCode] = value }
            entity.userId?.let { value -> it[PayTransferTable.userId] = value }
            entity.userType?.let { value -> it[PayTransferTable.userType] = value }
            entity.merchantTransferId?.let { value -> it[PayTransferTable.merchantTransferId] = value }
            entity.subject?.let { value -> it[PayTransferTable.subject] = value }
            entity.price?.let { value -> it[PayTransferTable.price] = value }
            entity.userAccount?.let { value -> it[PayTransferTable.userAccount] = value }
            entity.userName?.let { value -> it[PayTransferTable.userName] = value }
            entity.status?.let { value -> it[PayTransferTable.status] = value }
            entity.successTime?.let { value -> it[PayTransferTable.successTime] = value }
            entity.notifyUrl?.let { value -> it[PayTransferTable.notifyUrl] = value }
            entity.userIp?.let { value -> it[PayTransferTable.userIp] = value }
            entity.channelExtras?.let { value -> it[PayTransferTable.channelExtras] = value }
            entity.channelTransferNo?.let { value -> it[PayTransferTable.channelTransferNo] = value }
            entity.channelErrorCode?.let { value -> it[PayTransferTable.channelErrorCode] = value }
            entity.channelErrorMsg?.let { value -> it[PayTransferTable.channelErrorMsg] = value }
            entity.channelNotifyData?.let { value -> it[PayTransferTable.channelNotifyData] = value }
            entity.channelPackageInfo?.let { value -> it[PayTransferTable.channelPackageInfo] = value }
            entity.updater?.let { value -> it[PayTransferTable.updater] = value }
            it[PayTransferTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayTransferTable.update(where = { conditions(PayTransferTable.id eq id) }) { it[PayTransferTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayTransferTable.update(where = { conditions(PayTransferTable.id inList ids) }) { it[PayTransferTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayTransferTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayTransferTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayTransferDO().apply {
        id = row[PayTransferTable.id]
        no = row[PayTransferTable.no]
        appId = row[PayTransferTable.appId]
        channelId = row[PayTransferTable.channelId]
        channelCode = row[PayTransferTable.channelCode]
        userId = row[PayTransferTable.userId]
        userType = row[PayTransferTable.userType]
        merchantTransferId = row[PayTransferTable.merchantTransferId]
        subject = row[PayTransferTable.subject]
        price = row[PayTransferTable.price]
        userAccount = row[PayTransferTable.userAccount]
        userName = row[PayTransferTable.userName]
        status = row[PayTransferTable.status]
        successTime = row[PayTransferTable.successTime]
        notifyUrl = row[PayTransferTable.notifyUrl]
        userIp = row[PayTransferTable.userIp]
        channelExtras = row[PayTransferTable.channelExtras]
        channelTransferNo = row[PayTransferTable.channelTransferNo]
        channelErrorCode = row[PayTransferTable.channelErrorCode]
        channelErrorMsg = row[PayTransferTable.channelErrorMsg]
        channelNotifyData = row[PayTransferTable.channelNotifyData]
        channelPackageInfo = row[PayTransferTable.channelPackageInfo]
        creator = row[PayTransferTable.creator]
        createTime = row[PayTransferTable.createTime]
        updater = row[PayTransferTable.updater]
        updateTime = row[PayTransferTable.updateTime]
        deleted = row[PayTransferTable.deleted]
        tenantId = row[PayTransferTable.tenantId]
    }
}
