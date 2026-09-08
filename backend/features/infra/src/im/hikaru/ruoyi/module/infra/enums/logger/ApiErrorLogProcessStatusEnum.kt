package im.hikaru.ruoyi.module.infra.enums.logger

/**
 * API 异常数据的处理状态 (迁移自 Java)
 *
 * @author 芋道源码
 */
enum class ApiErrorLogProcessStatusEnum(val status: Int, val label: String) {
    /** 未处理 */
    INIT(0, "未处理"),
    /** 已处理 */
    DONE(1, "已处理"),
    /** 已忽略 */
    IGNORE(2, "已忽略"),
    ;
}
