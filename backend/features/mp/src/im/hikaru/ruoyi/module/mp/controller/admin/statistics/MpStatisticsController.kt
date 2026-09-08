package im.hikaru.ruoyi.module.mp.controller.admin.statistics

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo.*
import im.hikaru.ruoyi.module.mp.convert.statistics.MpStatisticsConvert
import im.hikaru.ruoyi.module.mp.service.statistics.MpStatisticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "管理后台 - 公众号统计")
@RestController
@RequestMapping("/mp/statistics")
@Validated
class MpStatisticsController(
    private val mpStatisticsService: MpStatisticsService,
) {
    @GetMapping("/user-summary")
    @Operation(summary = "获得粉丝增减数据")
    @PreAuthorize("@ss.hasPermission('mp:statistics:query')")
    fun getUserSummary(getReqVO: MpStatisticsGetReqVO): CommonResult<List<MpStatisticsUserSummaryRespVO>> = CommonResult.success(MpStatisticsConvert.convertUserSummary(mpStatisticsService.getUserSummary(requireNotNull(getReqVO.accountId), requireNotNull(getReqVO.date))))

    @GetMapping("/user-cumulate")
    @Operation(summary = "获得粉丝累计数据")
    @PreAuthorize("@ss.hasPermission('mp:statistics:query')")
    fun getUserCumulate(getReqVO: MpStatisticsGetReqVO): CommonResult<List<MpStatisticsUserCumulateRespVO>> = CommonResult.success(MpStatisticsConvert.convertUserCumulate(mpStatisticsService.getUserCumulate(requireNotNull(getReqVO.accountId), requireNotNull(getReqVO.date))))

    @GetMapping("/upstream-message")
    @Operation(summary = "获取消息发送概况数据")
    @PreAuthorize("@ss.hasPermission('mp:statistics:query')")
    fun getUpstreamMessage(getReqVO: MpStatisticsGetReqVO): CommonResult<List<MpStatisticsUpstreamMessageRespVO>> = CommonResult.success(MpStatisticsConvert.convertUpstreamMessage(mpStatisticsService.getUpstreamMessage(requireNotNull(getReqVO.accountId), requireNotNull(getReqVO.date))))

    @GetMapping("/interface-summary")
    @Operation(summary = "获取消息发送概况数据")
    @PreAuthorize("@ss.hasPermission('mp:statistics:query')")
    fun getInterfaceSummary(getReqVO: MpStatisticsGetReqVO): CommonResult<List<MpStatisticsInterfaceSummaryRespVO>> = CommonResult.success(MpStatisticsConvert.convertInterfaceSummary(mpStatisticsService.getInterfaceSummary(requireNotNull(getReqVO.accountId), requireNotNull(getReqVO.date))))
}
