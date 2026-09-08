package im.hikaru.ruoyi.module.pay.dal.mysql.order

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderPageReqVO
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PayOrderDao {
    fun selectById(id: Long): PayOrderDO? = transaction { PayOrderTable.selectAll().where { conditions(PayOrderTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByNo(no: String): PayOrderDO? = transaction { PayOrderTable.selectAll().where { conditions(PayOrderTable.no eq no) }.singleOrNull()?.let(::toEntity) }
    fun selectByAppIdAndMerchantOrderId(appId: Long, merchantOrderId: String): PayOrderDO? = transaction {
        PayOrderTable.selectAll().where { conditions(PayOrderTable.appId eq appId, PayOrderTable.merchantOrderId eq merchantOrderId) }
            .singleOrNull()?.let(::toEntity)
    }
    fun selectByIds(ids: Collection<Long>): List<PayOrderDO> = if (ids.isEmpty()) emptyList() else transaction { PayOrderTable.selectAll().where { conditions(PayOrderTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<PayOrderDO> = transaction { PayOrderTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { PayOrderTable.selectAll().where { conditions() }.count() }
    fun selectCountByAppId(appId: Long): Long = transaction { PayOrderTable.selectAll().where { conditions(PayOrderTable.appId eq appId) }.count() }
    fun selectListByStatus(status: Int): List<PayOrderDO> = transaction { PayOrderTable.selectAll().where { conditions(PayOrderTable.status eq status) }.map(::toEntity) }
    fun selectListByStatusAndExpireTimeLt(status: Int, expireTime: java.time.LocalDateTime): List<PayOrderDO> = transaction {
        PayOrderTable.selectAll().where {
            conditions(PayOrderTable.status eq status, PayOrderTable.expireTime less expireTime.toKotlinLocalDateTime())
        }.map(::toEntity)
    }
    fun selectPage(req: PayOrderPageReqVO): PageResult<PayOrderDO> = selectPageInternal(
        req.pageNo, req.pageSize, req.appId, req.channelCode, req.merchantOrderId,
        req.channelOrderNo, req.no, req.status, req.createTime
    )
    fun selectList(req: PayOrderExportReqVO): List<PayOrderDO> = selectPageInternal(
        1, PageParam.PAGE_SIZE_NONE, req.appId, req.channelCode, req.merchantOrderId,
        req.channelOrderNo, req.no, req.status, req.createTime
    ).list
    private fun selectPageInternal(pageNo: Int, pageSize: Int, appId: Long?, channelCode: String?, merchantOrderId: String?, channelOrderNo: String?, no: String?, status: Int?, createTime: Array<java.time.LocalDateTime>?): PageResult<PayOrderDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        appId?.let { ops += PayOrderTable.appId eq it }
        channelCode?.takeIf(String::isNotBlank)?.let { ops += PayOrderTable.channelCode eq it }
        merchantOrderId?.takeIf(String::isNotBlank)?.let { ops += PayOrderTable.merchantOrderId like "%$it%" }
        channelOrderNo?.takeIf(String::isNotBlank)?.let { ops += PayOrderTable.channelOrderNo like "%$it%" }
        no?.takeIf(String::isNotBlank)?.let { ops += PayOrderTable.no like "%$it%" }
        status?.let { ops += PayOrderTable.status eq it }
        createTime?.getOrNull(0)?.let { ops += PayOrderTable.createTime greaterEq it.toKotlinLocalDateTime() }
        createTime?.getOrNull(1)?.let { ops += PayOrderTable.createTime lessEq it.toKotlinLocalDateTime() }
        val all = PayOrderTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(PayOrderTable.id, SortOrder.DESC).map(::toEntity)
        val total = all.size.toLong()
        if (pageSize == PageParam.PAGE_SIZE_NONE) return@transaction PageResult(total, all)
        val from = ((pageNo - 1) * pageSize).coerceAtLeast(0)
        PageResult(total, if (from >= all.size) emptyList() else all.subList(from, minOf(from + pageSize, all.size)))
    }
    fun updateByIdAndStatus(id: Long, status: Int, entity: PayOrderDO): Int = updateByIdAndStatusInternal(id, status, entity)
    private fun updateByIdAndStatusInternal(id: Long, status: Int, entity: PayOrderDO): Int {
        entity.id = id
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction { PayOrderTable.update(where = { conditions(PayOrderTable.id eq id, PayOrderTable.status eq status) }) {
            entity.channelId?.let { value -> it[PayOrderTable.channelId] = value }
            entity.channelCode?.let { value -> it[PayOrderTable.channelCode] = value }
            entity.price?.let { value -> it[PayOrderTable.price] = value }
            entity.channelFeeRate?.let { value -> it[PayOrderTable.channelFeeRate] = value }
            entity.channelFeePrice?.let { value -> it[PayOrderTable.channelFeePrice] = value }
            entity.status?.let { value -> it[PayOrderTable.status] = value }
            entity.successTime?.let { value -> it[PayOrderTable.successTime] = value }
            entity.extensionId?.let { value -> it[PayOrderTable.extensionId] = value }
            entity.no?.let { value -> it[PayOrderTable.no] = value }
            entity.refundPrice?.let { value -> it[PayOrderTable.refundPrice] = value }
            entity.channelUserId?.let { value -> it[PayOrderTable.channelUserId] = value }
            entity.channelOrderNo?.let { value -> it[PayOrderTable.channelOrderNo] = value }
            entity.updater?.let { value -> it[PayOrderTable.updater] = value }
            it[PayOrderTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun incrementRefundPrice(id: Long, amount: Int): Int = transaction {
        val current = PayOrderTable.selectAll().where { conditions(PayOrderTable.id eq id) }.singleOrNull()?.let(::toEntity) ?: return@transaction 0
        val next = (current.refundPrice ?: 0) + amount
        updateByIdAndStatusInternal(id, current.status ?: 0, PayOrderDO().apply { refundPrice = next })
    }
    fun insert(entity: PayOrderDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { PayOrderTable.insert {
            it[PayOrderTable.appId] = entity.appId
            it[PayOrderTable.channelId] = entity.channelId
            it[PayOrderTable.channelCode] = entity.channelCode
            it[PayOrderTable.userId] = entity.userId
            it[PayOrderTable.userType] = entity.userType
            it[PayOrderTable.merchantOrderId] = entity.merchantOrderId
            it[PayOrderTable.subject] = entity.subject
            it[PayOrderTable.body] = entity.body
            it[PayOrderTable.notifyUrl] = entity.notifyUrl
            it[PayOrderTable.price] = entity.price
            it[PayOrderTable.channelFeeRate] = entity.channelFeeRate
            it[PayOrderTable.channelFeePrice] = entity.channelFeePrice
            it[PayOrderTable.status] = entity.status
            it[PayOrderTable.userIp] = entity.userIp
            it[PayOrderTable.expireTime] = entity.expireTime
            it[PayOrderTable.successTime] = entity.successTime
            it[PayOrderTable.extensionId] = entity.extensionId
            it[PayOrderTable.no] = entity.no
            it[PayOrderTable.refundPrice] = entity.refundPrice
            it[PayOrderTable.channelUserId] = entity.channelUserId
            it[PayOrderTable.channelOrderNo] = entity.channelOrderNo
            it[PayOrderTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[PayOrderTable.creator] = entity.creator
            it[PayOrderTable.updater] = entity.updater
            it[PayOrderTable.createTime] = requireNotNull(entity.createTime)
            it[PayOrderTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(PayOrderTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: PayOrderDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { PayOrderTable.update(where = { conditions(PayOrderTable.id eq id) }) {
            entity.appId?.let { value -> it[PayOrderTable.appId] = value }
            entity.channelId?.let { value -> it[PayOrderTable.channelId] = value }
            entity.channelCode?.let { value -> it[PayOrderTable.channelCode] = value }
            entity.userId?.let { value -> it[PayOrderTable.userId] = value }
            entity.userType?.let { value -> it[PayOrderTable.userType] = value }
            entity.merchantOrderId?.let { value -> it[PayOrderTable.merchantOrderId] = value }
            entity.subject?.let { value -> it[PayOrderTable.subject] = value }
            entity.body?.let { value -> it[PayOrderTable.body] = value }
            entity.notifyUrl?.let { value -> it[PayOrderTable.notifyUrl] = value }
            entity.price?.let { value -> it[PayOrderTable.price] = value }
            entity.channelFeeRate?.let { value -> it[PayOrderTable.channelFeeRate] = value }
            entity.channelFeePrice?.let { value -> it[PayOrderTable.channelFeePrice] = value }
            entity.status?.let { value -> it[PayOrderTable.status] = value }
            entity.userIp?.let { value -> it[PayOrderTable.userIp] = value }
            entity.expireTime?.let { value -> it[PayOrderTable.expireTime] = value }
            entity.successTime?.let { value -> it[PayOrderTable.successTime] = value }
            entity.extensionId?.let { value -> it[PayOrderTable.extensionId] = value }
            entity.no?.let { value -> it[PayOrderTable.no] = value }
            entity.refundPrice?.let { value -> it[PayOrderTable.refundPrice] = value }
            entity.channelUserId?.let { value -> it[PayOrderTable.channelUserId] = value }
            entity.channelOrderNo?.let { value -> it[PayOrderTable.channelOrderNo] = value }
            entity.updater?.let { value -> it[PayOrderTable.updater] = value }
            it[PayOrderTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { PayOrderTable.update(where = { conditions(PayOrderTable.id eq id) }) { it[PayOrderTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { PayOrderTable.update(where = { conditions(PayOrderTable.id inList ids) }) { it[PayOrderTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(PayOrderTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += PayOrderTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = PayOrderDO().apply {
        id = row[PayOrderTable.id]
        appId = row[PayOrderTable.appId]
        channelId = row[PayOrderTable.channelId]
        channelCode = row[PayOrderTable.channelCode]
        userId = row[PayOrderTable.userId]
        userType = row[PayOrderTable.userType]
        merchantOrderId = row[PayOrderTable.merchantOrderId]
        subject = row[PayOrderTable.subject]
        body = row[PayOrderTable.body]
        notifyUrl = row[PayOrderTable.notifyUrl]
        price = row[PayOrderTable.price]
        channelFeeRate = row[PayOrderTable.channelFeeRate]
        channelFeePrice = row[PayOrderTable.channelFeePrice]
        status = row[PayOrderTable.status]
        userIp = row[PayOrderTable.userIp]
        expireTime = row[PayOrderTable.expireTime]
        successTime = row[PayOrderTable.successTime]
        extensionId = row[PayOrderTable.extensionId]
        no = row[PayOrderTable.no]
        refundPrice = row[PayOrderTable.refundPrice]
        channelUserId = row[PayOrderTable.channelUserId]
        channelOrderNo = row[PayOrderTable.channelOrderNo]
        creator = row[PayOrderTable.creator]
        createTime = row[PayOrderTable.createTime]
        updater = row[PayOrderTable.updater]
        updateTime = row[PayOrderTable.updateTime]
        deleted = row[PayOrderTable.deleted]
        tenantId = row[PayOrderTable.tenantId]
    }
}
