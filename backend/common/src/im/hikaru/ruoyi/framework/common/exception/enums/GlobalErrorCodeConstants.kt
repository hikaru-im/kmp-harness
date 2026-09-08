package im.hikaru.ruoyi.framework.common.exception.enums

import im.hikaru.ruoyi.framework.common.exception.ErrorCode

/**
 * 全局错误码枚举
 * 0-999 系统异常编码保留
 *
 * 一般情况下，使用 HTTP 响应状态码 https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Status
 * 比较特殊的是，因为之前一直使用 0 作为成功，就不使用 200 啦。
 *
 * @author 芋道源码
 */
object GlobalErrorCodeConstants {
    val SUCCESS: ErrorCode = ErrorCode(0, "成功")

    // ========== 客户端错误段 ==========
    val BAD_REQUEST: ErrorCode = ErrorCode(400, "请求参数不正确")
    val UNAUTHORIZED: ErrorCode = ErrorCode(401, "账号未登录")
    val FORBIDDEN: ErrorCode = ErrorCode(403, "没有该操作权限")
    val NOT_FOUND: ErrorCode = ErrorCode(404, "请求未找到")
    val METHOD_NOT_ALLOWED: ErrorCode = ErrorCode(405, "请求方法不正确")
    val LOCKED: ErrorCode = ErrorCode(423, "请求失败，请稍后重试") // 并发请求，不允许
    val TOO_MANY_REQUESTS: ErrorCode = ErrorCode(429, "请求过于频繁，请稍后重试")

    // ========== 服务端错误段 ==========
    val INTERNAL_SERVER_ERROR: ErrorCode = ErrorCode(500, "系统异常")
    val NOT_IMPLEMENTED: ErrorCode = ErrorCode(501, "功能未实现/未开启")
    val ERROR_CONFIGURATION: ErrorCode = ErrorCode(502, "错误的配置项")

    // ========== 自定义错误段 ==========
    val REPEATED_REQUESTS: ErrorCode = ErrorCode(900, "重复请求，请稍后重试") // 重复请求
    val DEMO_DENY: ErrorCode = ErrorCode(901, "演示模式，禁止写操作")

    val UNKNOWN: ErrorCode = ErrorCode(999, "未知错误")
}
