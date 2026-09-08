package im.hikaru.ruoyi.framework.common.exception

/**
 * 服务器异常 Exception
 */
class ServerException : RuntimeException {
    /** 全局错误码 */
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

    fun setCode(code: Int?): ServerException {
        this.code = code
        return this
    }

    fun setMessage(message: String?): ServerException {
        this.message = message
        return this
    }
}
