package im.hikaru.ruoyi.framework.quartz.core.enums

/**
 * Quartz Job Data 的 key 枚举 (迁移自 Java)
 */
enum class JobDataKeyEnum {
    JOB_ID,
    JOB_HANDLER_NAME,
    JOB_HANDLER_PARAM,
    JOB_RETRY_COUNT, // 最大重试次数
    JOB_RETRY_INTERVAL, // 每次重试间隔
}
