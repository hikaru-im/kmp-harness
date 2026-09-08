package im.hikaru.ruoyi.framework.common.exception

/**
 * 业务逻辑异常 Exception
 */
class ServiceException : RuntimeException {
    /** 业务错误码 */
    var code: Int? = null
        private set

    override var message: String? = null
        private set

    /** 空构造方法，避免反序列化问题 */
    constructor()

    constructor(errorCode: ErrorCode) {
        this.code = errorCode.code
        this.message = errorCode.msg
    }

    constructor(code: Int?, message: String?) {
        this.code = code
        this.message = message
    }

    fun setCode(code: Int?): ServiceException {
        this.code = code
        return this
    }

    fun setMessage(message: String?): ServiceException {
        this.message = message
        return this
    }
}
