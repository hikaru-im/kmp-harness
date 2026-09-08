package im.hikaru.ruoyi.module.mp.controller.admin.material

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.*
import im.hikaru.ruoyi.module.mp.convert.material.MpMaterialConvert
import im.hikaru.ruoyi.module.mp.service.material.MpMaterialService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号素材")
@RestController
@RequestMapping("/mp/material")
@Validated
class MpMaterialController(
    private val mpMaterialService: MpMaterialService,
) {
    @Operation(summary = "上传临时素材")
    @PostMapping("/upload-temporary")
    @PreAuthorize("@ss.hasPermission('mp:material:upload-temporary')")
    fun uploadTemporaryMaterial(@Valid reqVO: MpMaterialUploadTemporaryReqVO): CommonResult<MpMaterialUploadRespVO> = CommonResult.success(MpMaterialConvert.convert(mpMaterialService.uploadTemporaryMaterial(reqVO)))

    @Operation(summary = "上传永久素材")
    @PostMapping("/upload-permanent")
    @PreAuthorize("@ss.hasPermission('mp:material:upload-permanent')")
    fun uploadPermanentMaterial(@Valid reqVO: MpMaterialUploadPermanentReqVO): CommonResult<MpMaterialUploadRespVO> = CommonResult.success(MpMaterialConvert.convert(mpMaterialService.uploadPermanentMaterial(reqVO)))

    @Operation(summary = "删除素材")
    @DeleteMapping("/delete-permanent")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mp:material:delete')")
    fun deleteMaterial(@RequestParam("id") id: Long): CommonResult<Boolean> { mpMaterialService.deleteMaterial(id); return CommonResult.success(true) }

    @Operation(summary = "上传图文内容中的图片")
    @PostMapping("/upload-news-image")
    @PreAuthorize("@ss.hasPermission('mp:material:upload-news-image')")
    fun uploadNewsImage(@Valid reqVO: MpMaterialUploadNewsImageReqVO): CommonResult<String> = CommonResult.success(mpMaterialService.uploadNewsImage(reqVO))

    @Operation(summary = "获得素材分页")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('mp:material:query')")
    fun getMaterialPage(@Valid pageReqVO: MpMaterialPageReqVO): CommonResult<PageResult<MpMaterialRespVO>> = CommonResult.success(MpMaterialConvert.convertPage(mpMaterialService.getMaterialPage(pageReqVO)))
}
