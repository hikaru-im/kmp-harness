package im.hikaru.ruoyi.module.system.controller.admin.sms

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.system.framework.sms.core.enums.SmsChannelEnum
import im.hikaru.ruoyi.module.system.service.sms.SmsSendService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - SMS callback")
@RestController
@RequestMapping("/system/sms/callback")
class SmsCallbackController(
    private val smsSendService: SmsSendService,
) {
    @PostMapping("/aliyun")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "Receive Aliyun SMS callback")
    fun aliyun(@RequestBody body: String): CommonResult<Boolean> = receive(SmsChannelEnum.ALIYUN, body)

    @PostMapping("/tencent")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "Receive Tencent SMS callback")
    fun tencent(@RequestBody body: String): CommonResult<Boolean> = receive(SmsChannelEnum.TENCENT, body)

    @PostMapping("/huawei")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "Receive Huawei SMS callback")
    fun huawei(@RequestBody body: String): CommonResult<Boolean> = receive(SmsChannelEnum.HUAWEI, body)

    @PostMapping("/qiniu")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "Receive Qiniu SMS callback")
    fun qiniu(@RequestBody body: String): CommonResult<Boolean> = receive(SmsChannelEnum.QINIU, body)

    private fun receive(channel: SmsChannelEnum, body: String): CommonResult<Boolean> {
        smsSendService.receiveSmsStatus(channel.code, body)
        return CommonResult.success(true)
    }
}
