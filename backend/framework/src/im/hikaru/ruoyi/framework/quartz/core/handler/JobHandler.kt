package im.hikaru.ruoyi.framework.quartz.core.handler

/**
 * 任务处理器 (迁移自 Java)
 *
 * @author 芋道源码
 */
fun interface JobHandler {

    /**
     * 执行任务
     *
     * @param param 参数
     * @return 结果
     */
    @Throws(Exception::class)
    fun execute(param: String): String?
}
