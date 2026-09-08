package im.hikaru.ruoyi.module.infra.service.job

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.quartz.core.service.JobLogFrameworkService
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.log.JobLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobLogDO

/**
 * Job 日志 Service 接口 (迁移自 Java)
 * @author 芋道源码
 */
interface JobLogService : JobLogFrameworkService {

    /**
     * 获得定时任务
     *
     * @param id 编号
     * @return 定时任务
     */
    fun getJobLog(id: Long): JobLogDO?

    /**
     * 获得定时任务分页
     *
     * @param pageReqVO 分页查询
     * @return 定时任务分页
     */
    fun getJobLogPage(pageReqVO: JobLogPageReqVO): PageResult<JobLogDO>

    /**
     * 清理 exceedDay 天前的任务日志
     *
     * @param exceedDay   超过多少天就进行清理
     * @param deleteLimit 清理的间隔条数
     */
    fun cleanJobLog(exceedDay: Int, deleteLimit: Int): Int
}
