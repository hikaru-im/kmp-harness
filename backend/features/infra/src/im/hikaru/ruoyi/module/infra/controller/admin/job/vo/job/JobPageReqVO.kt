package im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 定时任务分页 Request VO")
class JobPageReqVO : PageParam() {
    @Schema(description = "任务名称，模糊匹配", example = "测试任务")
    var name: String? = null

    @Schema(description = "任务状态，参见 JobStatusEnum 枚举", example = "1")
    var status: Int? = null

    @Schema(description = "处理器的名字，模糊匹配", example = "sysUserSessionTimeoutJob")
    var handlerName: String? = null
}
