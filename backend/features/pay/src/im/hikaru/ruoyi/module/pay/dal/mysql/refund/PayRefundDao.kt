package im.hikaru.ruoyi.module.pay.dal.mysql.refund

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundPageReqVO
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
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

object PayRefundDao {
    fun selectById(id: Long): PayRefundDO? = transaction { PayRefundTable.selectAll().where { conditions(PayRefundTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByNo(no: String): PayRefundDO? = transaction { PayRefundTable.selectAll().where { conditions(PayRefundTable.no eq no) }.singleOrNull()?.let(::toEntity) }
    fun selectByAppIdAndMerchantRefundId(appId: Long, merchantRefundId: String): PayRefundDO? = transaction {
        PayRefundTable.selectAll().where { conditions(PayRefundTable.appId eq appId, PayRefundTable.merchantRefundId eq merchantRefundId) }
            .singleOrNull()?.let(::toEntity)
    }
    fun selectByAppIdAndNo(appId: Long, no: String): PayRefundDO? = transaction {
        PayRefundTable.selectAll().where { conditions(PayRefundTable.appId eq appId, PayRefundTable.no eq no) }
            .singleOrNull()?.let(::toEntity)
    }
    fun selectByIds(ids: Collection<Long>): List<PayRefundDO> = if (ids.isEmpty()) emptyList() else transaction { PayRefundTable.selectAll().where { conditions(PayRefundTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayRefundDO> = transaction { PayRefundTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayRefundTable.selectAll().where { conditions() }.count() }
    fun selectCountByAppId(appId: Long): Long = transaction { PayRefundTable.selectAll().where { conditions(PayRefundTable.appId eq appId) }.count() }
    fun selectCountByAppIdAndOrderId(appId: Long, orderId: Long, status: Int): Long = transaction {
        PayRefundTable.selectAll().where { conditions(PayRefundTable.appId eq appId, PayRefundTable.orderId eq orderId, PayRefundTable.status eq status) }.count()
    }
    fun selectListByStatus(status: Int): List<PayRefundDO> = transaction { PayRefundTable.selectAll().where { conditions(PayRefundTable.status eq status) }.map(::toEntity) }
    fun selectPage(req: PayRefundPageReqVO): PageResult<PayRefundDO> = selectPageInternal(req.pageNo, req.pageSize, req.appId, req.channelCode, req.merchantOrderId, req.merchantRefundId, req.channelOrderNo, req.channelRefundNo, req.status, req.createTime)
    fun selectList(req: PayRefundExportReqVO): List<PayRefundDO> = selectPageInternal(1, PageParam.PAGE_SIZE_NONE, req.appId, req.channelCode, req.merchantOrderId, req.merchantRefundId, req.channelOrderNo, req.channelRefundNo, req.status, req.createTime).list
    private fun selectPageInternal(pageNo: Int, pageSize: Int, appId: Long?, channelCode: String?, merchantOrderId: String?, merchantRefundId: String?, channelOrderNo: String?, channelRefundNo: String?, status: Int?, createTime: Array<java.time.LocalDateTime>?): PageResult<PayRefundDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        appId?.let { ops += PayRefundTable.appId eq it }
        channelCode?.takeIf(String::isNotBlank)?.let { ops += PayRefundTable.channelCode eq it }
        merchantOrderId?.takeIf(String::isNotBlank)?.let { ops += PayRefundTable.merchantOrderId like "%$it%" }
        merchantRefundId?.takeIf(String::isNotBlank)?.let { ops += PayRefundTable.merchantRefundId like "%$it%" }
        channelOrderNo?.takeIf(String::isNotBlank)?.let { ops += PayRefundTable.channelOrderNo like "%$it%" }
        channelRefundNo?.takeIf(String::isNotBlank)?.let { ops += PayRefundTable.channelRefundNo like "%$it%" }
        status?.let { ops += PayRefundTable.status eq it }
        createTime?.getOrNull(0)?.let { ops += PayRefundTable.createTime greaterEq it.toKotlinLocalDateTime() }
        createTime?.getOrNull(1)?.let { ops += PayRefundTable.createTime lessEq it.toKotlinLocalDateTime() }
        val all = PayRefundTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PayRefundTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong()
        if (pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((pageNo - 1) * pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + pageSize, all.size)))
    }
    fun updateByIdAndStatus(id: Long, status: Int, entity: PayRefundDO): Int {
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayRefundTable.update(where = { conditions(PayRefundTable.id eq id, PayRefundTable.status eq status) }) {
            entity.status?.let { value -> it[PayRefundTable.status] = value }
            entity.successTime?.let { value -> it[PayRefundTable.successTime] = value }
            entity.channelRefundNo?.let { value -> it[PayRefundTable.channelRefundNo] = value }
            entity.channelErrorCode?.let { value -> it[PayRefundTable.channelErrorCode] = value }
            entity.channelErrorMsg?.let { value -> it[PayRefundTable.channelErrorMsg] = value }
            entity.channelNotifyData?.let { value -> it[PayRefundTable.channelNotifyData] = value }
            entity.updater?.let { value -> it[PayRefundTable.updater] = value }
            it[PayRefundTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun insert(entity: PayRefundDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayRefundTable.insert {
            it[PayRefundTable.no] = entity.no
            it[PayRefundTable.appId] = entity.appId
            it[PayRefundTable.channelId] = entity.channelId
            it[PayRefundTable.channelCode] = entity.channelCode
            it[PayRefundTable.orderId] = entity.orderId
            it[PayRefundTable.orderNo] = entity.orderNo
            it[PayRefundTable.userId] = entity.userId
            it[PayRefundTable.userType] = entity.userType
            it[PayRefundTable.merchantOrderId] = entity.merchantOrderId
            it[PayRefundTable.merchantRefundId] = entity.merchantRefundId
            it[PayRefundTable.notifyUrl] = entity.notifyUrl
            it[PayRefundTable.status] = entity.status
            it[PayRefundTable.payPrice] = entity.payPrice
            it[PayRefundTable.refundPrice] = entity.refundPrice
            it[PayRefundTable.reason] = entity.reason
            it[PayRefundTable.userIp] = entity.userIp
            it[PayRefundTable.channelOrderNo] = entity.channelOrderNo
            it[PayRefundTable.channelRefundNo] = entity.channelRefundNo
            it[PayRefundTable.successTime] = entity.successTime
            it[PayRefundTable.channelErrorCode] = entity.channelErrorCode
            it[PayRefundTable.channelErrorMsg] = entity.channelErrorMsg
            it[PayRefundTable.channelNotifyData] = entity.channelNotifyData
            it[PayRefundTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayRefundTable.creator] = entity.creator
            it[PayRefundTable.updater] = entity.updater
            it[PayRefundTable.createTime] = requireNotNull(entity.createTime)
            it[PayRefundTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayRefundTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayRefundDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayRefundTable.update(where = { conditions(PayRefundTable.id eq id) }) {
            entity.no?.let { value -> it[PayRefundTable.no] = value }
            entity.appId?.let { value -> it[PayRefundTable.appId] = value }
            entity.channelId?.let { value -> it[PayRefundTable.channelId] = value }
            entity.channelCode?.let { value -> it[PayRefundTable.channelCode] = value }
            entity.orderId?.let { value -> it[PayRefundTable.orderId] = value }
            entity.orderNo?.let { value -> it[PayRefundTable.orderNo] = value }
            entity.userId?.let { value -> it[PayRefundTable.userId] = value }
            entity.userType?.let { value -> it[PayRefundTable.userType] = value }
            entity.merchantOrderId?.let { value -> it[PayRefundTable.merchantOrderId] = value }
            entity.merchantRefundId?.let { value -> it[PayRefundTable.merchantRefundId] = value }
            entity.notifyUrl?.let { value -> it[PayRefundTable.notifyUrl] = value }
            entity.status?.let { value -> it[PayRefundTable.status] = value }
            entity.payPrice?.let { value -> it[PayRefundTable.payPrice] = value }
            entity.refundPrice?.let { value -> it[PayRefundTable.refundPrice] = value }
            entity.reason?.let { value -> it[PayRefundTable.reason] = value }
            entity.userIp?.let { value -> it[PayRefundTable.userIp] = value }
            entity.channelOrderNo?.let { value -> it[PayRefundTable.channelOrderNo] = value }
            entity.channelRefundNo?.let { value -> it[PayRefundTable.channelRefundNo] = value }
            entity.successTime?.let { value -> it[PayRefundTable.successTime] = value }
            entity.channelErrorCode?.let { value -> it[PayRefundTable.channelErrorCode] = value }
            entity.channelErrorMsg?.let { value -> it[PayRefundTable.channelErrorMsg] = value }
            entity.channelNotifyData?.let { value -> it[PayRefundTable.channelNotifyData] = value }
            entity.updater?.let { value -> it[PayRefundTable.updater] = value }
            it[PayRefundTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayRefundTable.update(where = { conditions(PayRefundTable.id eq id) }) { it[PayRefundTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayRefundTable.update(where = { conditions(PayRefundTable.id inList ids) }) { it[PayRefundTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayRefundTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayRefundTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayRefundDO().apply {
        id = row[PayRefundTable.id]
        no = row[PayRefundTable.no]
        appId = row[PayRefundTable.appId]
        channelId = row[PayRefundTable.channelId]
        channelCode = row[PayRefundTable.channelCode]
        orderId = row[PayRefundTable.orderId]
        orderNo = row[PayRefundTable.orderNo]
        userId = row[PayRefundTable.userId]
        userType = row[PayRefundTable.userType]
        merchantOrderId = row[PayRefundTable.merchantOrderId]
        merchantRefundId = row[PayRefundTable.merchantRefundId]
        notifyUrl = row[PayRefundTable.notifyUrl]
        status = row[PayRefundTable.status]
        payPrice = row[PayRefundTable.payPrice]
        refundPrice = row[PayRefundTable.refundPrice]
        reason = row[PayRefundTable.reason]
        userIp = row[PayRefundTable.userIp]
        channelOrderNo = row[PayRefundTable.channelOrderNo]
        channelRefundNo = row[PayRefundTable.channelRefundNo]
        successTime = row[PayRefundTable.successTime]
        channelErrorCode = row[PayRefundTable.channelErrorCode]
        channelErrorMsg = row[PayRefundTable.channelErrorMsg]
        channelNotifyData = row[PayRefundTable.channelNotifyData]
        creator = row[PayRefundTable.creator]
        createTime = row[PayRefundTable.createTime]
        updater = row[PayRefundTable.updater]
        updateTime = row[PayRefundTable.updateTime]
        deleted = row[PayRefundTable.deleted]
        tenantId = row[PayRefundTable.tenantId]
    }
}
