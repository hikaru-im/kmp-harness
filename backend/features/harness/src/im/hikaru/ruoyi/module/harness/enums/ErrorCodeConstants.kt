package im.hikaru.ruoyi.module.harness.enums

import im.hikaru.ruoyi.framework.common.exception.ErrorCode

/** Harness Relay 模块使用的 RuoYi 数值错误码。 */
object ErrorCodeConstants {
    val HOST_NOT_FOUND = ErrorCode(1_023_000_000, "Harness Host 不存在或无权访问")
    val HOST_NOT_CONNECTED = ErrorCode(1_023_000_001, "Harness Host 当前不在线")
    val HOST_REPLACED = ErrorCode(1_023_000_002, "Harness Host 连接已被新的 generation 替换")

    val DUPLICATE_REQUEST = ErrorCode(1_023_001_000, "Harness Relay 请求编号重复")
    val HOST_DISCONNECTED = ErrorCode(1_023_001_001, "Harness Host 已断开连接")
}
