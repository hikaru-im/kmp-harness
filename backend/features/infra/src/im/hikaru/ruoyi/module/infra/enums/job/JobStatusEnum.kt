package im.hikaru.ruoyi.module.infra.enums.job

/**
 * 任务状态枚举 (迁移自 Java)
 *
 * 对应 Quartz 触发器的状态集合见源码 Quartz Constants；在迁移版本里暂以 Set 表达，
 * 由 SchedulerManager 在同步 Quartz 状态时使用。这里仅保留状态值与是否暂停/正常语义。
 *
 * @author 芋道源码
 */
enum class JobStatusEnum(val status: Int, val label: String) {
    /** 初始化中 */
    INIT(0, "初始化中"),
    /** 开启 */
    NORMAL(1, "开启"),
    /** 暂停 */
    STOP(2, "暂停"),
    ;

    companion object {
        @JvmStatic
        fun getIfPresent(status: Int?): JobStatusEnum? =
            status?.let { s -> entries.firstOrNull { it.status == s } }
    }
}
