package im.hikaru.ruoyi.framework.web.core.handler

import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiErrorLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.collection.SetUtils
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.common.util.monitor.TracerUtils
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import com.google.common.util.concurrent.UncheckedExecutionException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.exc.InvalidFormatException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.METHOD_NOT_ALLOWED
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_IMPLEMENTED

/**
 * 全局异常处理器，将 Exception 翻译成 CommonResult + 对应的异常编号 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：Hutool ExceptionUtil → JDK Throwable；Hutool StrUtil/CollUtil/MapUtil → Kotlin stdlib
 *
 * @author 芋道源码
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val applicationName: String,
    private val apiErrorLogApi: ApiErrorLogCommonApi,
) {

    /**
     * 处理所有异常，主要是提供给 Filter 使用
     */
    fun allExceptionHandler(request: HttpServletRequest, ex: Throwable): CommonResult<*> {
        return when (ex) {
            is MissingServletRequestParameterException -> missingServletRequestParameterExceptionHandler(ex)
            is MethodArgumentTypeMismatchException -> methodArgumentTypeMismatchExceptionHandler(ex)
            is MethodArgumentNotValidException -> methodArgumentNotValidExceptionExceptionHandler(ex)
            is BindException -> bindExceptionHandler(ex)
            is ConstraintViolationException -> constraintViolationExceptionHandler(ex)
            is ValidationException -> validationException(ex)
            is MaxUploadSizeExceededException -> maxUploadSizeExceededExceptionHandler(ex)
            is NoHandlerFoundException -> noHandlerFoundExceptionHandler(ex)
            is NoResourceFoundException -> noResourceFoundExceptionHandler(request, ex)
            is HttpRequestMethodNotSupportedException -> httpRequestMethodNotSupportedExceptionHandler(ex)
            is HttpMediaTypeNotSupportedException -> httpMediaTypeNotSupportedExceptionHandler(ex)
            is ServiceException -> serviceExceptionHandler(ex)
            is AccessDeniedException -> accessDeniedExceptionHandler(request, ex)
            else -> defaultExceptionHandler(request, ex)
        }
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingServletRequestParameterExceptionHandler(ex: MissingServletRequestParameterException): CommonResult<*> {
        log.warn("[missingServletRequestParameterExceptionHandler]", ex)
        return CommonResult.error<Any>(BAD_REQUEST.code, "请求参数缺失:${ex.parameterName}")
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun methodArgumentTypeMismatchExceptionHandler(ex: MethodArgumentTypeMismatchException): CommonResult<*> {
        log.warn("[methodArgumentTypeMismatchExceptionHandler]", ex)
        return CommonResult.error<Any>(BAD_REQUEST.code, "请求参数类型错误:${ex.message}")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun methodArgumentNotValidExceptionExceptionHandler(ex: MethodArgumentNotValidException): CommonResult<*> {
        log.warn("[methodArgumentNotValidExceptionExceptionHandler]", ex)
        // 获取 errorMessage
        var errorMessage: String? = null
        val fieldError: FieldError? = ex.bindingResult.fieldError
        if (fieldError == null) {
            // 组合校验
            val allErrors = ex.bindingResult.allErrors
            if (allErrors.isNotEmpty()) {
                errorMessage = allErrors[0].defaultMessage
            }
        } else {
            errorMessage = fieldError.defaultMessage
        }
        return if (errorMessage.isNullOrEmpty()) {
            CommonResult.error<Any>(BAD_REQUEST)
        } else {
            CommonResult.error<Any>(BAD_REQUEST.code, "请求参数不正确:$errorMessage")
        }
    }

    @ExceptionHandler(BindException::class)
    fun bindExceptionHandler(ex: BindException): CommonResult<*> {
        log.warn("[handleBindException]", ex)
        val fieldError: FieldError = ex.fieldError ?: return CommonResult.error<Any>(BAD_REQUEST)
        return CommonResult.error<Any>(BAD_REQUEST.code, "请求参数不正确:${fieldError.defaultMessage}")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun methodArgumentTypeInvalidFormatExceptionHandler(ex: HttpMessageNotReadableException): CommonResult<*> {
        log.warn("[methodArgumentTypeInvalidFormatExceptionHandler]", ex)
        if (ex.cause is InvalidFormatException) {
            val invalidFormatException = ex.cause as InvalidFormatException
            return CommonResult.error<Any>(BAD_REQUEST.code, "请求参数类型错误:${invalidFormatException.value}")
        }
        if (ex.message?.startsWith("Required request body is missing") == true) {
            return CommonResult.error<Any>(BAD_REQUEST.code, "请求参数类型错误: request body 缺失")
        }
        val request = ServletUtils.getRequest()
            ?: return CommonResult.error<Any>(INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR.msg)
        return defaultExceptionHandler(request, ex)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolationExceptionHandler(ex: ConstraintViolationException): CommonResult<*> {
        log.warn("[constraintViolationExceptionHandler]", ex)
        val constraintViolation: ConstraintViolation<*> = ex.constraintViolations.iterator().next()
        return CommonResult.error<Any>(BAD_REQUEST.code, "请求参数不正确:${constraintViolation.message}")
    }

    @ExceptionHandler(ValidationException::class)
    fun validationException(ex: ValidationException): CommonResult<*> {
        log.warn("[constraintViolationExceptionHandler]", ex)
        return CommonResult.error<Any>(BAD_REQUEST)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun maxUploadSizeExceededExceptionHandler(ex: MaxUploadSizeExceededException): CommonResult<*> =
        CommonResult.error<Any>(BAD_REQUEST.code, "上传文件过大，请调整后重试")

    @ExceptionHandler(NoHandlerFoundException::class)
    fun noHandlerFoundExceptionHandler(ex: NoHandlerFoundException): CommonResult<*> {
        log.warn("[noHandlerFoundExceptionHandler]", ex)
        return CommonResult.error<Any>(NOT_FOUND.code, "请求地址不存在:${ex.requestURL}")
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun noResourceFoundExceptionHandler(req: HttpServletRequest, ex: NoResourceFoundException): CommonResult<*> {
        log.warn("[noResourceFoundExceptionHandler]", ex)
        return CommonResult.error<Any>(NOT_FOUND.code, "请求地址不存在:${ex.resourcePath}")
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun httpRequestMethodNotSupportedExceptionHandler(ex: HttpRequestMethodNotSupportedException): CommonResult<*> {
        log.warn("[httpRequestMethodNotSupportedExceptionHandler]", ex)
        return CommonResult.error<Any>(METHOD_NOT_ALLOWED.code, "请求方法不正确:${ex.message}")
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun httpMediaTypeNotSupportedExceptionHandler(ex: HttpMediaTypeNotSupportedException): CommonResult<*> {
        log.warn("[httpMediaTypeNotSupportedExceptionHandler]", ex)
        return CommonResult.error<Any>(BAD_REQUEST.code, "请求类型不正确:${ex.message}")
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDeniedExceptionHandler(req: HttpServletRequest, ex: AccessDeniedException): CommonResult<*> {
        log.warn("[accessDeniedExceptionHandler][userId({}) 无法访问 url({})]", WebFrameworkUtils.getLoginUserId(req), req.requestURL, ex)
        return CommonResult.error<Any>(FORBIDDEN)
    }

    @ExceptionHandler(UncheckedExecutionException::class)
    fun uncheckedExecutionExceptionHandler(req: HttpServletRequest, ex: UncheckedExecutionException): CommonResult<*> =
        allExceptionHandler(req, ex.cause ?: ex)

    @ExceptionHandler(ServiceException::class)
    fun serviceExceptionHandler(ex: ServiceException): CommonResult<*> {
        // 不包含的时候，才进行打印，避免 ex 堆栈过多
        if (!IGNORE_ERROR_MESSAGES.contains(ex.message)) {
            try {
                val stackTraces = ex.stackTrace
                for (stackTrace in stackTraces) {
                    if (stackTrace.className != ServiceExceptionUtil::class.java.name) {
                        log.warn("[serviceExceptionHandler]\n\t{}", stackTrace)
                        break
                    }
                }
            } catch (ignored: Exception) {
                // 忽略日志，避免影响主流程
            }
        }
        return if (CommonResult.isSuccess(ex.code)) {
            CommonResult.error<Any>(INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR.msg)
        } else {
            CommonResult.error<Any>(ex.code, ex.message)
        }
    }

    @ExceptionHandler(Exception::class)
    fun defaultExceptionHandler(req: HttpServletRequest, ex: Throwable): CommonResult<*> {
        // 特殊：如果是 ServiceException 的异常，则直接返回
        if (ex.cause is ServiceException) {
            return serviceExceptionHandler(ex.cause as ServiceException)
        }
        // 情况一：处理表不存在的异常
        handleTableNotExists(ex)?.let { return it }
        // 情况二：处理异常
        log.error("[defaultExceptionHandler]", ex)
        // 插入异常日志
        createExceptionLog(req, ex)
        // 返回 ERROR CommonResult
        return CommonResult.error<Any>(INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR.msg)
    }

    private fun createExceptionLog(req: HttpServletRequest, e: Throwable) {
        val errorLog = ApiErrorLogCreateReqDTO()
        try {
            buildExceptionLog(errorLog, req, e)
            apiErrorLogApi.createApiErrorLogAsync(errorLog)
        } catch (th: Throwable) {
            log.error("[createExceptionLog][url({}) log({}) 发生异常]", req.requestURI, JsonUtils.toJsonString(errorLog), th)
        }
    }

    private fun buildExceptionLog(errorLog: ApiErrorLogCreateReqDTO, request: HttpServletRequest, e: Throwable) {
        // 处理用户信息
        errorLog.userId = WebFrameworkUtils.getLoginUserId(request)
        errorLog.userType = WebFrameworkUtils.getLoginUserType(request)
        // 设置异常字段
        errorLog.exceptionName = e.javaClass.name
        errorLog.exceptionMessage = getMessage(e)
        errorLog.exceptionRootCauseMessage = getRootCauseMessage(e)
        errorLog.exceptionStackTrace = stacktraceToString(e)
        val stackTraceElements = e.stackTrace
        check(stackTraceElements.isNotEmpty()) { "异常 stackTraceElements 不能为空" }
        val stackTraceElement = stackTraceElements[0]
        errorLog.exceptionClassName = stackTraceElement.className
        errorLog.exceptionFileName = stackTraceElement.fileName
        errorLog.exceptionMethodName = stackTraceElement.methodName
        errorLog.exceptionLineNumber = stackTraceElement.lineNumber
        // 设置其它字段
        errorLog.traceId = TracerUtils.getTraceId()
        errorLog.applicationName = applicationName
        errorLog.requestUrl = request.requestURI
        val requestParams = mapOf(
            "query" to ServletUtils.getParamMap(request),
            "body" to ServletUtils.getBody(request),
        )
        errorLog.requestParams = JsonUtils.toJsonString(requestParams)
        errorLog.requestMethod = request.method
        errorLog.userAgent = ServletUtils.getUserAgent(request)
        errorLog.userIp = ServletUtils.getClientIP(request)
        errorLog.exceptionTime = LocalDateTime.now()
    }

    /**
     * 处理 Table 不存在的异常情况
     */
    private fun handleTableNotExists(ex: Throwable): CommonResult<*>? {
        val message = getRootCauseMessage(ex)
        if (!message.contains("doesn't exist")) return null
        // 1. 数据报表
        if (message.contains("report_")) {
            log.error("[报表模块 yudao-module-report - 表结构未导入][参考 https://cloud.iocoder.cn/report/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[报表模块 yudao-module-report - 表结构未导入][参考 https://cloud.iocoder.cn/report/ 开启]")
        }
        // 2. 工作流
        if (message.contains("bpm_")) {
            log.error("[工作流模块 yudao-module-bpm - 表结构未导入][参考 https://cloud.iocoder.cn/bpm/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[工作流模块 yudao-module-bpm - 表结构未导入][参考 https://cloud.iocoder.cn/bpm/ 开启]")
        }
        // 3. 微信公众号
        if (message.contains("mp_")) {
            log.error("[微信公众号 yudao-module-mp - 表结构未导入][参考 https://cloud.iocoder.cn/mp/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[微信公众号 yudao-module-mp - 表结构未导入][参考 https://cloud.iocoder.cn/mp/build/ 开启]")
        }
        // 4. 商城系统
        if (listOf("product_", "promotion_", "trade_").any { message.contains(it) }) {
            log.error("[商城系统 yudao-module-mall - 已禁用][参考 https://cloud.iocoder.cn/mall/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[商城系统 yudao-module-mall - 已禁用][参考 https://cloud.iocoder.cn/mall/build/ 开启]")
        }
        // 5. ERP 系统
        if (message.contains("erp_")) {
            log.error("[ERP 系统 yudao-module-erp - 表结构未导入][参考 https://cloud.iocoder.cn/erp/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[ERP 系统 yudao-module-erp - 表结构未导入][参考 https://cloud.iocoder.cn/erp/build/ 开启]")
        }
        // 6. WMS 仓库管理系统
        if (message.contains("wms_")) {
            log.error("[WMS 仓库管理系统 yudao-module-wms - 表结构未导入][参考 https://doc.iocoder.cn/wms/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[WMS 仓库管理系统 yudao-module-wms - 表结构未导入][参考 https://doc.iocoder.cn/wms/build/ 开启]")
        }
        // 7. CRM 系统
        if (message.contains("crm_")) {
            log.error("[CRM 系统 yudao-module-crm - 表结构未导入][参考 https://cloud.iocoder.cn/crm/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[CRM 系统 yudao-module-crm - 表结构未导入][参考 https://cloud.iocoder.cn/crm/build/ 开启]")
        }
        // 8. MES 系统
        if (message.contains("mes_")) {
            log.error("[MES 系统 yudao-module-mes - 表结构未导入][参考 https://doc.iocoder.cn/mes/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[MES 系统 yudao-module-mes - 表结构未导入][参考 https://doc.iocoder.cn/mes/build/ 开启]")
        }
        // 9. IM 即时通讯
        if (message.contains("im_")) {
            log.error("[IM 即时通讯 yudao-module-im - 表结构未导入][参考 https://doc.iocoder.cn/im/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[IM 即时通讯 yudao-module-im - 表结构未导入][参考 https://doc.iocoder.cn/im/build/ 开启]")
        }
        // 10. 支付平台
        if (message.contains("pay_")) {
            log.error("[支付模块 yudao-module-pay - 表结构未导入][参考 https://cloud.iocoder.cn/pay/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[支付模块 yudao-module-pay - 表结构未导入][参考 https://cloud.iocoder.cn/pay/build/ 开启]")
        }
        // 11. AI 大模型
        if (message.contains("ai_")) {
            log.error("[AI 大模型 yudao-module-ai - 表结构未导入][参考 https://cloud.iocoder.cn/ai/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[AI 大模型 yudao-module-ai - 表结构未导入][参考 https://cloud.iocoder.cn/ai/build/ 开启]")
        }
        // 12. IoT 物联网
        if (message.contains("iot_")) {
            log.error("[IoT 物联网 yudao-module-iot - 表结构未导入][参考 https://doc.iocoder.cn/iot/build/ 开启]")
            return CommonResult.error<Any>(NOT_IMPLEMENTED.code, "[IoT 物联网 yudao-module-iot - 表结构未导入][参考 https://doc.iocoder.cn/iot/build/ 开启]")
        }
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

        /** 忽略的 ServiceException 错误提示，避免打印过多 logger */
        val IGNORE_ERROR_MESSAGES: Set<String> = SetUtils.asSet("无效的刷新令牌")

        private fun getMessage(e: Throwable): String = e.message ?: ""

        private fun getRootCauseMessage(e: Throwable): String {
            var current: Throwable = e
            while (current.cause != null && current.cause !== current) {
                current = current.cause!!
            }
            return current.message ?: ""
        }

        private fun stacktraceToString(e: Throwable): String {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }
}
