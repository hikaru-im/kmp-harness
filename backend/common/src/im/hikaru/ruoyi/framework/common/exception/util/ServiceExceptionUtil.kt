package im.hikaru.ruoyi.framework.common.exception.util

import im.hikaru.ruoyi.framework.common.exception.ErrorCode
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import org.slf4j.LoggerFactory

/**
 * [ServiceException] 工具类
 *
 * 目的在于，格式化异常信息提示。
 * 考虑到 String.format 在参数不正确时会报错，因此使用 {} 作为占位符，并使用 [doFormat] 方法来格式化
 */
object ServiceExceptionUtil {
    private val log = LoggerFactory.getLogger(ServiceExceptionUtil::class.java)

    // ========== 和 ServiceException 的集成 ==========

    @JvmStatic
    fun exception(errorCode: ErrorCode): ServiceException =
        exception0(errorCode.code, errorCode.msg)

    @JvmStatic
    fun exception(errorCode: ErrorCode, vararg params: Any?): ServiceException =
        exception0(errorCode.code, errorCode.msg, *params)

    @JvmStatic
    fun exception0(code: Int, messagePattern: String, vararg params: Any?): ServiceException {
        val message = doFormat(code, messagePattern, *params)
        return ServiceException(code, message)
    }

    @JvmStatic
    fun invalidParamException(messagePattern: String, vararg params: Any?): ServiceException =
        exception0(GlobalErrorCodeConstants.BAD_REQUEST.code, messagePattern, *params)

    // ========== 格式化方法 ==========

    /**
     * 将错误编号对应的消息使用 params 进行格式化。
     */
    @JvmStatic
    fun doFormat(code: Int, messagePattern: String, vararg params: Any?): String {
        val sbuf = StringBuilder(messagePattern.length + 50)
        var i = 0
        var j: Int
        var l: Int
        l = 0
        while (l < params.size) {
            j = messagePattern.indexOf("{}", i)
            if (j == -1) {
                log.error("[doFormat][参数过多：错误码({})|错误内容({})|参数({})", code, messagePattern, params)
                return if (i == 0) messagePattern else sbuf.append(messagePattern.substring(i)).toString()
            } else {
                sbuf.append(messagePattern, i, j)
                sbuf.append(params[l])
                i = j + 2
            }
            l++
        }
        if (messagePattern.indexOf("{}", i) != -1) {
            log.error("[doFormat][参数过少：错误码({})|错误内容({})|参数({})", code, messagePattern, params)
        }
        sbuf.append(messagePattern.substring(i))
        return sbuf.toString()
    }
}
