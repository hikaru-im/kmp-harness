package im.hikaru.ruoyi.module.pay.controller.app.wallet

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.date.DateUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionRespVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionSummaryRespVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletTransactionConvert
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletTransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Wallet Transaction")
@RestController
@RequestMapping("/pay/wallet-transaction")
@Validated
class AppPayWalletTransactionController(
    private val transactionService: PayWalletTransactionService,
) {
    @GetMapping("/page")
    @Operation(summary = "Get wallet transaction page")
    fun getWalletTransactionPage(
        @Valid pageReqVO: AppPayWalletTransactionPageReqVO,
    ): CommonResult<PageResult<AppPayWalletTransactionRespVO>> = CommonResult.success(
        PayWalletTransactionConvert.appPage(
            transactionService.getWalletTransactionPage(userId(), UserTypeEnum.MEMBER.value, pageReqVO),
        ),
    )

    @GetMapping("/get-summary")
    @Operation(summary = "Get wallet transaction summary")
    @Parameter(name = "createTime", description = "Time range", required = true)
    fun getWalletTransactionSummary(
        @RequestParam("createTime")
        @DateTimeFormat(pattern = DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
        createTime: Array<LocalDateTime>,
    ): CommonResult<AppPayWalletTransactionSummaryRespVO> = CommonResult.success(
        transactionService.getWalletTransactionSummary(userId(), UserTypeEnum.MEMBER.value, createTime),
    )

    private fun userId(): Long = requireNotNull(WebFrameworkUtils.getLoginUserId())
}
