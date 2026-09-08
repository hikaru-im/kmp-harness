package im.hikaru.ruoyi.module.mp.convert.statistics

import im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo.MpStatisticsInterfaceSummaryRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo.MpStatisticsUpstreamMessageRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo.MpStatisticsUserCumulateRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo.MpStatisticsUserSummaryRespVO
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeInterfaceResult
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeMsgResult
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeUserCumulate
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeUserSummary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

object MpStatisticsConvert {
    fun convertUserSummary(list: List<WxDataCubeUserSummary>): List<MpStatisticsUserSummaryRespVO> = list.map { bean ->
        MpStatisticsUserSummaryRespVO().apply {
            refDate = parseDate(bean.refDate)
            userSource = bean.userSource
            newUser = bean.newUser
            cancelUser = bean.cancelUser
        }
    }

    fun convertUserCumulate(list: List<WxDataCubeUserCumulate>): List<MpStatisticsUserCumulateRespVO> = list.map { bean ->
        MpStatisticsUserCumulateRespVO().apply {
            refDate = parseDate(bean.refDate)
            cumulateUser = bean.cumulateUser
        }
    }

    fun convertUpstreamMessage(list: List<WxDataCubeMsgResult>): List<MpStatisticsUpstreamMessageRespVO> = list.map { bean ->
        MpStatisticsUpstreamMessageRespVO().apply {
            refDate = parseDate(bean.refDate)
            messageUser = bean.msgUser
            messageCount = bean.msgCount
        }
    }

    fun convertInterfaceSummary(list: List<WxDataCubeInterfaceResult>): List<MpStatisticsInterfaceSummaryRespVO> = list.map { bean ->
        MpStatisticsInterfaceSummaryRespVO().apply {
            refDate = parseDate(bean.refDate)
            callbackCount = bean.callbackCount
            failCount = bean.failCount
            totalTimeCost = bean.totalTimeCost
            maxTimeCost = bean.maxTimeCost
        }
    }

    private fun parseDate(value: String?): LocalDateTime? = value?.let { LocalDate.parse(it).atStartOfDay() }
    private fun parseDate(value: Date?): LocalDateTime? = value?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
}
