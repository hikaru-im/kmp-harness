package im.hikaru.ruoyi.framework.common.pojo

import im.hikaru.ruoyi.framework.common.exception.ErrorCode
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.Serializable
import java.util.Objects

/**
 * 通用返回
 *
 * @param T 数据泛型
 */
class CommonResult<T> : Serializable {
    /**
     * 错误码
     *
     * @see ErrorCode.code
     */
    var code: Int? = null

    /**
     * 错误提示，用户可阅读
     *
     * @see ErrorCode.msg
     */
    var msg: String? = null

    /** 返回数据 */
    var data: T? = null

    constructor()

    constructor(code: Int?, msg: String?, data: T? = null) {
        this.code = code
        this.msg = msg
        this.data = data
    }

    /**
     * 判断是否有异常。如果有，则抛出 [ServiceException] 异常
     */
    fun checkError() {
        if (isSuccess) {
            return
        }
        // 业务异常
        throw ServiceException(code, msg)
    }

    /**
     * 判断是否有异常。如果有，则抛出 [ServiceException] 异常
     * 如果没有，则返回 [data] 数据
     */
    @get:JsonIgnore // 避免 jackson 序列化
    val checkedData: T?
        get() {
            checkError()
            return data
        }

    @get:JsonIgnore // 避免 jackson 序列化
    val isSuccess: Boolean
        get() = isSuccess(code)

    @get:JsonIgnore // 避免 jackson 序列化
    val isError: Boolean
        get() = !isSuccess

    companion object {
        /**
         * 将传入的 result 对象，转换成另外一个泛型结果的对象
         *
         * 因为 A 方法返回的 CommonResult 对象，不满足调用其的 B 方法的返回，所以需要进行转换。
         */
        @JvmStatic
        fun <T> error(result: CommonResult<*>): CommonResult<T> = error(result.code, result.msg)

        @JvmStatic
        fun <T> error(code: Int?, message: String?): CommonResult<T> {
            check(!GlobalErrorCodeConstants.SUCCESS.code.equals(code)) { "code 必须是错误的！" }
            return CommonResult(code, message, null)
        }

        @JvmStatic
        fun <T> error(errorCode: ErrorCode, vararg params: Any?): CommonResult<T> {
            check(!GlobalErrorCodeConstants.SUCCESS.code.equals(errorCode.code)) { "code 必须是错误的！" }
            return CommonResult(
                errorCode.code,
                ServiceExceptionUtil.doFormat(errorCode.code, errorCode.msg, *params),
                null,
            )
        }

        @JvmStatic
        fun <T> error(errorCode: ErrorCode): CommonResult<T> = error(errorCode.code, errorCode.msg)

        @JvmStatic
        fun <T> success(data: T): CommonResult<T> =
            CommonResult(GlobalErrorCodeConstants.SUCCESS.code, "", data)

        @JvmStatic
        fun <T> error(serviceException: ServiceException): CommonResult<T> =
            error(serviceException.code, serviceException.message)

        @JvmStatic
        fun isSuccess(code: Int?): Boolean =
            Objects.equals(code, GlobalErrorCodeConstants.SUCCESS.code)
    }
}
