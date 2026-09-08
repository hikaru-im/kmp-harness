package im.hikaru.ruoyi.module.sync.controller.app.vo

import im.hikaru.contracts.sync.SyncPushPlatform
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "Register a mobile device for sync wakeup hints")
class AppSyncPushDeviceReqVO {
    @field:NotBlank
    @field:Size(max = 512)
    var token: String = ""

    @field:NotNull
    var platform: SyncPushPlatform? = null
}

@Schema(description = "Unregister a mobile device from sync wakeup hints")
class AppSyncPushDeviceUnregisterReqVO {
    @field:NotBlank
    @field:Size(max = 512)
    var token: String = ""
}
