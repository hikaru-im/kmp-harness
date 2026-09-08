package im.hikaru.ruoyi.framework.common.exception

/**
 * 错误码对象
 *
 * 全局错误码，占用 [0, 999], 参见 [im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants]
 * 业务异常错误码，占用 [1 000 000 000, +∞)，参见 [im.hikaru.ruoyi.framework.common.exception.enums.ServiceErrorCodeRange]
 */
data class ErrorCode(val code: Int, val msg: String)
