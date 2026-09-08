package im.hikaru.ruoyi.module.pay.api.transfer

import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateRespDTO
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.convert.transfer.PayTransferConvert
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.weixin.WxPayClientConfig
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayTransferApiImpl(
    private val payTransferService: PayTransferService,
    private val payChannelService: PayChannelService,
) : PayTransferApi {
    override fun createTransfer(reqDTO: PayTransferCreateReqDTO): PayTransferCreateRespDTO =
        payTransferService.createTransfer(reqDTO)

    override fun getTransfer(id: Long): PayTransferRespDTO {
        val transfer = payTransferService.getTransfer(id)
        val channel = payChannelService.getChannel(requireNotNull(transfer.channelId))
        return PayTransferConvert.api(transfer).apply {
            channelMchId = (channel.config as? WxPayClientConfig)?.mchId
        }
    }
}
