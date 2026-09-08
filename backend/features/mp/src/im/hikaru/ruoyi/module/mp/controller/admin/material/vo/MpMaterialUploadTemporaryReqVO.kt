package im.hikaru.ruoyi.module.mp.controller.admin.material.vo

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.web.multipart.MultipartFile

@Schema(description = "管理后台 - 公众号素材上传临时 Request VO")
class MpMaterialUploadTemporaryReqVO {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
    @field:Schema(description = "文件类型 参见 WxConsts.MediaFileType 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "image")
    @field:NotEmpty(message = "文件类型不能为空")
    var type: String? = null
    @field:Schema(description = "文件附件", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "文件不能为空")
    @JsonIgnore
    var file: MultipartFile? = null
}
