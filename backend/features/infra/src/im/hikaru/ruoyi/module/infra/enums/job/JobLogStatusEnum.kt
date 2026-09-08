package im.hikaru.ruoyi.module.infra.enums.job

/**
 * 任务日志的状态枚举 (迁移自 Java)
 *
 * @author 芋道源码
 */
enum class JobLogStatusEnum(val status: Int) {
    /** 运行中 */
    RUNNING(0),
    /** 成功 */
    SUCCESS(1),
    /** 失败 */
    FAILURE(2),
    ;
}
