package im.hikaru.ruoyi.module.pay.api.wallet

import im.hikaru.ruoyi.module.pay.api.wallet.dto.PayWalletAddBalanceReqDTO
import im.hikaru.ruoyi.module.pay.api.wallet.dto.PayWalletRespDTO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletConvert
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayWalletApiImpl(
    private val payWalletService: PayWalletService,
) : PayWalletApi {
    override fun addWalletBalance(reqDTO: PayWalletAddBalanceReqDTO) {
        val wallet = payWalletService.getOrCreateWallet(requireNotNull(reqDTO.userId), requireNotNull(reqDTO.userType))
        val bizType = requireNotNull(PayWalletBizTypeEnum.valueOf(reqDTO.bizType)) {
            "Unknown wallet business type: ${reqDTO.bizType}"
        }
        payWalletService.addWalletBalance(
            requireNotNull(wallet.id),
            requireNotNull(reqDTO.bizId),
            bizType,
            requireNotNull(reqDTO.price),
        )
    }

    override fun getOrCreateWallet(userId: Long, userType: Int): PayWalletRespDTO =
        PayWalletConvert.api(payWalletService.getOrCreateWallet(userId, userType))
}
