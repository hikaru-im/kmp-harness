package im.hikaru.ruoyi.framework.operatelog.core.service

import im.hikaru.ruoyi.framework.common.biz.system.logger.OperateLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.util.monitor.TracerUtils
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import com.mzt.logapi.beans.LogRecord
import com.mzt.logapi.service.ILogRecordService
import org.slf4j.LoggerFactory

/** Persists BizLog records through [OperateLogCommonApi]. */
class LogRecordServiceImpl(
    private val operateLogApi: OperateLogCommonApi,
) : ILogRecordService {

    override fun record(logRecord: LogRecord) {
        val request = OperateLogCreateReqDTO()
        try {
            request.traceId = TracerUtils.getTraceId()
            fillUserFields(request)
            fillModuleFields(request, logRecord)
            fillRequestFields(request)
            operateLogApi.createOperateLogAsync(request)
        } catch (ex: Throwable) {
            logger.error(
                "[record][url({}) log({}) failed]",
                request.requestUrl,
                request,
                ex,
            )
        }
    }

    override fun queryLog(bizNo: String, type: String): List<LogRecord> =
        throw UnsupportedOperationException("Use OperateLogCommonApi to query operation logs")

    override fun queryLogByBizNo(bizNo: String, type: String, subType: String): List<LogRecord> =
        throw UnsupportedOperationException("Use OperateLogCommonApi to query operation logs")

    private fun fillUserFields(request: OperateLogCreateReqDTO) {
        val loginUser = SecurityFrameworkUtils.getLoginUser() ?: return
        request.userId = loginUser.id
        request.userType = loginUser.userType
    }

    private fun fillRequestFields(request: OperateLogCreateReqDTO) {
        val servletRequest = ServletUtils.getRequest() ?: return
        request.requestMethod = servletRequest.method
        request.requestUrl = servletRequest.requestURI
        request.userIp = ServletUtils.getClientIP(servletRequest)
        request.userAgent = ServletUtils.getUserAgent(servletRequest)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LogRecordServiceImpl::class.java)

        internal fun fillModuleFields(request: OperateLogCreateReqDTO, logRecord: LogRecord) {
            request.type = logRecord.type
            request.subType = logRecord.subType
            request.bizId = logRecord.bizNo.toLong()
            request.action = logRecord.action
            request.extra = logRecord.extra
        }
    }
}
