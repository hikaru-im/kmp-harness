package im.hikaru.ruoyi.module.mp.service.statistics

import java.time.LocalDateTime
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeInterfaceResult
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeMsgResult
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeUserCumulate
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeUserSummary

interface MpStatisticsService {
    fun getUserSummary(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeUserSummary>
    fun getUserCumulate(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeUserCumulate>
    fun getUpstreamMessage(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeMsgResult>
    fun getInterfaceSummary(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeInterfaceResult>
}
