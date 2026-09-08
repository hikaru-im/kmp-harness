package im.hikaru.ruoyi.module.pay.service.notify

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayOrderNotifyReqDTO
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayRefundNotifyReqDTO
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayTransferNotifyReqDTO
import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyLogDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.notify.PayNotifyTaskDO
import im.hikaru.ruoyi.module.pay.dal.mysql.notify.PayNotifyLogDao
import im.hikaru.ruoyi.module.pay.dal.mysql.notify.PayNotifyTaskDao
import im.hikaru.ruoyi.module.pay.dal.redis.notify.PayNotifyLockRedisDAO
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyStatusEnum
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import im.hikaru.ruoyi.module.pay.framework.job.config.PayJobConfiguration
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayNotifyServiceImpl(
    private val payProperties: PayProperties,
    private val orderServiceProvider: ObjectProvider<PayOrderService>,
    private val refundServiceProvider: ObjectProvider<PayRefundService>,
    private val transferServiceProvider: ObjectProvider<PayTransferService>,
    @param:Qualifier(PayJobConfiguration.NOTIFY_THREAD_POOL_TASK_EXECUTOR)
    private val notifyTaskExecutor: ThreadPoolTaskExecutor,
    private val notifyLockRedisDAO: PayNotifyLockRedisDAO,
) : PayNotifyService {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build()

    override fun createPayNotifyTask(type: Int, dataId: Long) {
        if (PayNotifyTaskDao.selectByTypeAndDataId(type, dataId) != null) return

        val source = resolveSource(type, dataId)
        if (source.notifyUrl.isNullOrBlank()) {
            log.warn("[createPayNotifyTask] callback URL is empty: type={}, dataId={}", type, dataId)
            return
        }
        val task = PayNotifyTaskDO().apply {
            appId = source.appId
            this.type = type
            this.dataId = dataId
            merchantOrderId = source.merchantOrderId
            merchantRefundId = source.merchantRefundId
            merchantTransferId = source.merchantTransferId
            status = PayNotifyStatusEnum.WAITING.status
            nextNotifyTime = LocalDateTime.now().toKotlinLocalDateTime()
            notifyTimes = 0
            maxNotifyTimes = payProperties.maxNotifyTimes.coerceAtLeast(1)
            notifyUrl = source.notifyUrl
            tenantId = TenantContextHolder.getTenantId()
        }
        PayNotifyTaskDao.insert(task)
        notifyTaskExecutor.execute { executeNotify(task) }
    }

    override fun executeNotify(): Int {
        val tasks = PayNotifyTaskDao.selectDue()
        if (tasks.isEmpty()) return 0

        val latch = CountDownLatch(tasks.size)
        tasks.forEach { task ->
            notifyTaskExecutor.execute {
                try {
                    executeNotify(task)
                } finally {
                    latch.countDown()
                }
            }
        }
        try {
            if (!latch.await(NOTIFY_TIMEOUT.seconds, TimeUnit.SECONDS)) {
                log.error(
                    "[executeNotify] callback batch timed out: total={}, remaining={}",
                    tasks.size,
                    latch.count,
                )
            }
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ex
        }
        return tasks.size
    }

    override fun getNotifyTask(id: Long): PayNotifyTaskDO =
        PayNotifyTaskDao.selectById(id) ?: throw IllegalArgumentException("Notify task $id does not exist")

    override fun getNotifyTaskPage(pageReqVO: PayNotifyTaskPageReqVO): PageResult<PayNotifyTaskDO> =
        PayNotifyTaskDao.selectPage(pageReqVO)

    override fun getNotifyLogList(taskId: Long): List<PayNotifyLogDO> =
        PayNotifyLogDao.selectListByTaskId(taskId)

    private fun executeNotify(snapshot: PayNotifyTaskDO) {
        val taskId = requireNotNull(snapshot.id)
        notifyLockRedisDAO.withLock(taskId, LOCK_LEASE_TIME) {
            val task = PayNotifyTaskDao.selectById(taskId) ?: return@withLock
            if (task.notifyTimes != snapshot.notifyTimes || task.status !in ACTIVE_STATUSES) {
                log.warn(
                    "[executeNotify] skipped stale callback task: id={}, expectedTimes={}, actualTimes={}, status={}",
                    taskId,
                    snapshot.notifyTimes,
                    task.notifyTimes,
                    task.status,
                )
                return@withLock
            }
            executeNotify0(task)
        }
    }

    private fun executeNotify0(task: PayNotifyTaskDO) {
        var invokeResult: CommonResult<*>? = null
        var invokeException: Throwable? = null
        try {
            invokeResult = invokeCallback(task)
        } catch (ex: Throwable) {
            invokeException = ex
        }

        val lastExecuteTime = LocalDateTime.now()
        val notifyResult = calculateNotifyResult(task, invokeResult, invokeException, lastExecuteTime)
        val response = invokeException?.rootCauseMessage()
            ?: JsonUtils.toJsonString(invokeResult)
        val updated = PayNotifyTaskDao.updateResultAndInsertLog(
            task = task,
            expectedNotifyTimes = task.notifyTimes ?: 0,
            newStatus = notifyResult.status,
            newNotifyTimes = notifyResult.notifyTimes,
            lastExecuteTime = notifyResult.lastExecuteTime,
            nextNotifyTime = notifyResult.nextNotifyTime,
            response = response,
        )
        if (!updated) {
            log.warn("[executeNotify0] callback result was not persisted because task changed: id={}", task.id)
        }
    }

    private fun invokeCallback(task: PayNotifyTaskDO): CommonResult<*> {
        val requestBody = buildNotifyRequest(task)
        val headers = mutableMapOf(
            HttpHeaders.CONTENT_TYPE to MediaType.APPLICATION_JSON_VALUE,
            HttpHeaders.ACCEPT to MediaType.APPLICATION_JSON_VALUE,
        )
        TenantUtils.addTenantHeader(headers, task.tenantId)
        val requestBuilder = HttpRequest.newBuilder(URI.create(requireNotNull(task.notifyUrl)))
            .timeout(NOTIFY_TIMEOUT)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    JsonUtils.toJsonString(requestBody),
                    StandardCharsets.UTF_8,
                ),
            )
        headers.forEach(requestBuilder::header)

        val response = httpClient.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
        )
        if (response.statusCode() !in 200..299) {
            throw IOException("Callback returned HTTP ${response.statusCode()}: ${response.body()}")
        }
        return JsonUtils.parseObject(response.body(), CommonResult::class.java)
            ?: throw IOException("Callback returned an empty response")
    }

    private fun resolveSource(type: Int, dataId: Long): NotifySource = when (type) {
        PayNotifyTypeEnum.ORDER.type -> orderServiceProvider.getObject().getOrder(dataId).let {
            NotifySource(
                appId = it.appId,
                notifyUrl = it.notifyUrl,
                merchantOrderId = it.merchantOrderId,
            )
        }
        PayNotifyTypeEnum.REFUND.type -> refundServiceProvider.getObject().getRefund(dataId).let {
            NotifySource(
                appId = it.appId,
                notifyUrl = it.notifyUrl,
                merchantOrderId = it.merchantOrderId,
                merchantRefundId = it.merchantRefundId,
            )
        }
        PayNotifyTypeEnum.TRANSFER.type -> transferServiceProvider.getObject().getTransfer(dataId).let {
            NotifySource(
                appId = it.appId,
                notifyUrl = it.notifyUrl,
                merchantTransferId = it.merchantTransferId,
            )
        }
        else -> throw IllegalArgumentException("Unknown pay notify type: $type")
    }

    private fun Throwable.rootCauseMessage(): String {
        var root = this
        while (root.cause != null && root.cause !== root) root = root.cause!!
        return root.message ?: root::class.java.name
    }

    private data class NotifySource(
        val appId: Long?,
        val notifyUrl: String?,
        val merchantOrderId: String? = null,
        val merchantRefundId: String? = null,
        val merchantTransferId: String? = null,
    )

    companion object {
        private val log = LoggerFactory.getLogger(PayNotifyServiceImpl::class.java)
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val NOTIFY_TIMEOUT: Duration = Duration.ofSeconds(120)
        private val LOCK_LEASE_TIME: Duration = NOTIFY_TIMEOUT.plusSeconds(10)
        private val ACTIVE_STATUSES = setOf(
            PayNotifyStatusEnum.WAITING.status,
            PayNotifyStatusEnum.REQUEST_SUCCESS.status,
            PayNotifyStatusEnum.REQUEST_FAILURE.status,
        )
    }
}

internal fun buildNotifyRequest(task: PayNotifyTaskDO): Any = when (task.type) {
    PayNotifyTypeEnum.ORDER.type -> PayOrderNotifyReqDTO().apply {
        merchantOrderId = task.merchantOrderId
        payOrderId = task.dataId
    }
    PayNotifyTypeEnum.REFUND.type -> PayRefundNotifyReqDTO().apply {
        merchantOrderId = task.merchantOrderId
        merchantRefundId = task.merchantRefundId
        payRefundId = task.dataId
    }
    PayNotifyTypeEnum.TRANSFER.type -> PayTransferNotifyReqDTO().apply {
        merchantTransferId = task.merchantTransferId
        payTransferId = task.dataId
    }
    else -> throw IllegalArgumentException("Unknown pay notify type: ${task.type}")
}

internal fun calculateNotifyResult(
    task: PayNotifyTaskDO,
    invokeResult: CommonResult<*>?,
    invokeException: Throwable?,
    lastExecuteTime: LocalDateTime,
): PayNotifyResult {
    val newNotifyTimes = (task.notifyTimes ?: 0) + 1
    val maxNotifyTimes = task.maxNotifyTimes ?: PayNotifyTaskDO.DEFAULT_MAX_NOTIFY_TIMES
    val success = invokeResult?.isSuccess == true
    val terminal = success || newNotifyTimes >= maxNotifyTimes
    val status = when {
        success -> PayNotifyStatusEnum.SUCCESS.status
        terminal -> PayNotifyStatusEnum.FAILURE.status
        invokeException != null -> PayNotifyStatusEnum.REQUEST_FAILURE.status
        else -> PayNotifyStatusEnum.REQUEST_SUCCESS.status
    }
    val nextNotifyTime = if (terminal) {
        null
    } else {
        val delayIndex = (newNotifyTimes - 1).coerceIn(
            0,
            PayNotifyTaskDO.NOTIFY_FREQUENCY_SECONDS.lastIndex,
        )
        lastExecuteTime.plusSeconds(PayNotifyTaskDO.NOTIFY_FREQUENCY_SECONDS[delayIndex])
            .toKotlinLocalDateTime()
    }
    return PayNotifyResult(
        status = status,
        notifyTimes = newNotifyTimes,
        lastExecuteTime = lastExecuteTime.toKotlinLocalDateTime(),
        nextNotifyTime = nextNotifyTime,
    )
}

internal data class PayNotifyResult(
    val status: Int,
    val notifyTimes: Int,
    val lastExecuteTime: kotlinx.datetime.LocalDateTime,
    val nextNotifyTime: kotlinx.datetime.LocalDateTime?,
)
