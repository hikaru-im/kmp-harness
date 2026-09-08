package im.hikaru.ruoyi.module.sync.controller.app

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchRespVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesQuery
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesRespVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncPushDeviceReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncPushDeviceUnregisterReqVO
import im.hikaru.ruoyi.module.sync.service.SyncCommandService
import im.hikaru.ruoyi.module.sync.service.SyncPushDeviceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - offline synchronization")
@RestController
@RequestMapping("/sync")
@Validated
class AppSyncController(
    private val syncCommandService: SyncCommandService,
    private val syncPushDeviceService: SyncPushDeviceService,
) {
    @PostMapping("/commands")
    @Operation(summary = "Submit replayable commands")
    fun submitCommands(
        @Valid @RequestBody request: AppSyncCommandBatchReqVO,
    ): CommonResult<AppSyncCommandBatchRespVO> = CommonResult.success(
        syncCommandService.submit(userId(), request),
    )

    @GetMapping("/changes")
    @Operation(summary = "Pull changes after a stable cursor")
    fun getChanges(
        @Valid @ModelAttribute query: AppSyncChangesQuery,
    ): CommonResult<AppSyncChangesRespVO> = CommonResult.success(
        syncCommandService.getChanges(userId(), query),
    )

    @PostMapping("/push-devices/register")
    @Operation(summary = "Register this device for best-effort sync wakeups")
    fun registerPushDevice(
        @Valid @RequestBody request: AppSyncPushDeviceReqVO,
    ): CommonResult<Boolean> {
        syncPushDeviceService.register(userId(), request.token, requireNotNull(request.platform))
        return CommonResult.success(true)
    }

    @PostMapping("/push-devices/unregister")
    @Operation(summary = "Stop sending sync wakeups to this device")
    fun unregisterPushDevice(
        @Valid @RequestBody request: AppSyncPushDeviceUnregisterReqVO,
    ): CommonResult<Boolean> {
        syncPushDeviceService.unregister(userId(), request.token)
        return CommonResult.success(true)
    }

    private fun userId(): Long = requireNotNull(WebFrameworkUtils.getLoginUserId())
}
