package im.hikaru.ruoyi.module.infra.service.job

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobDO
import org.quartz.SchedulerException

/**
 * 定时任务 Service 接口 (迁移自 Java)
 * @author 芋道源码
 */
interface JobService {

    /**
     * 创建定时任务
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    @Throws(SchedulerException::class)
    fun createJob(createReqVO: JobSaveReqVO): Long

    /**
     * 更新定时任务
     *
     * @param updateReqVO 更新信息
     */
    @Throws(SchedulerException::class)
    fun updateJob(updateReqVO: JobSaveReqVO)

    /**
     * 更新定时任务的状态
     *
     * @param id     任务编号
     * @param status 状态
     */
    @Throws(SchedulerException::class)
    fun updateJobStatus(id: Long, status: Int)

    /**
     * 触发定时任务
     *
     * @param id 任务编号
     */
    @Throws(SchedulerException::class)
    fun triggerJob(id: Long)

    /**
     * 同步定时任务
     *
     * 目的：自己存储的 Job 信息，强制同步到 Quartz 中
     */
    @Throws(SchedulerException::class)
    fun syncJob()

    /**
     * 删除定时任务
     *
     * @param id 编号
     */
    @Throws(SchedulerException::class)
    fun deleteJob(id: Long)

    /**
     * 批量删除定时任务
     *
     * @param ids 编号列表
     */
    @Throws(SchedulerException::class)
    fun deleteJobList(ids: List<Long>)

    /**
     * 获得定时任务
     *
     * @param id 编号
     * @return 定时任务
     */
    fun getJob(id: Long): JobDO?

    /**
     * 获得定时任务分页
     *
     * @param pageReqVO 分页查询
     * @return 定时任务分页
     */
    fun getJobPage(pageReqVO: JobPageReqVO): PageResult<JobDO>
}
