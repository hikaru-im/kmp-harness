package im.hikaru.ruoyi.module.mp.service.statistics

import im.hikaru.ruoyi.framework.common.exception.ErrorCode
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.STATISTICS_GET_INTERFACE_SUMMARY_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.STATISTICS_GET_UPSTREAM_MESSAGE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.STATISTICS_GET_USER_CUMULATE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.STATISTICS_GET_USER_SUMMARY_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import me.chanjar.weixin.common.error.WxErrorException
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeInterfaceResult
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeMsgResult
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeUserCumulate
import me.chanjar.weixin.mp.bean.datacube.WxDataCubeUserSummary
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@Service
class MpStatisticsServiceImpl(
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
) : MpStatisticsService {
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()
    override fun getUserSummary(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeUserSummary> =
        execute(STATISTICS_GET_USER_SUMMARY_FAIL) {
            mpServiceFactory.getRequiredMpService(accountId).dataCubeService.getUserSummary(date[0].toDate(), date[1].toDate())
        }

    override fun getUserCumulate(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeUserCumulate> =
        execute(STATISTICS_GET_USER_CUMULATE_FAIL) {
            mpServiceFactory.getRequiredMpService(accountId).dataCubeService.getUserCumulate(date[0].toDate(), date[1].toDate())
        }

    override fun getUpstreamMessage(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeMsgResult> =
        execute(STATISTICS_GET_UPSTREAM_MESSAGE_FAIL) {
            mpServiceFactory.getRequiredMpService(accountId).dataCubeService.getUpstreamMsg(date[0].toDate(), date[1].toDate())
        }

    override fun getInterfaceSummary(accountId: Long, date: Array<LocalDateTime>): List<WxDataCubeInterfaceResult> =
        execute(STATISTICS_GET_INTERFACE_SUMMARY_FAIL) {
            mpServiceFactory.getRequiredMpService(accountId).dataCubeService.getInterfaceSummary(date[0].toDate(), date[1].toDate())
        }

    private fun <T> execute(errorCode: ErrorCode, block: () -> T): T = try {
        block()
    } catch (ex: Exception) {
        val message = (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
        throw exception(errorCode, message)
    }

    private fun LocalDateTime.toDate(): Date = Date.from(atZone(ZoneId.systemDefault()).toInstant())
}
