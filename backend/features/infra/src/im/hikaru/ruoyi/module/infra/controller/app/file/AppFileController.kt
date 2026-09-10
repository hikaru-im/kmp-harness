package im.hikaru.ruoyi.module.infra.controller.app.file

import im.hikaru.contracts.infra.FilePresignedUrl
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.infra.controller.admin.file.toContract
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FileCreateReqVO
import im.hikaru.ruoyi.module.infra.controller.app.file.vo.AppFileUploadReqVO
import im.hikaru.ruoyi.module.infra.service.file.FileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "App - File storage")
@RestController
@RequestMapping("/infra/file")
@Validated
class AppFileController(
    private val fileService: FileService,
) {

    @PostMapping("/upload")
    @Operation(summary = "Upload a file")
    @Parameter(
        name = "file",
        description = "File attachment",
        required = true,
        schema = Schema(type = "string", format = "binary"),
    )
    fun uploadFile(@Valid uploadReqVO: AppFileUploadReqVO): CommonResult<String> {
        val file = requireNotNull(uploadReqVO.file)
        val content = file.inputStream.use { it.readBytes() }
        return CommonResult.success(
            fileService.createFile(content, file.originalFilename, uploadReqVO.directory, file.contentType),
        )
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "Get a presigned upload URL")
    @Parameters(
        Parameter(name = "name", description = "File name", required = true),
        Parameter(name = "directory", description = "Optional directory"),
    )
    fun getFilePresignedUrl(
        @RequestParam("name") name: String,
        @RequestParam(value = "directory", required = false) directory: String?,
    ): CommonResult<FilePresignedUrl> =
        CommonResult.success(fileService.presignPutUrl(name, directory).toContract())

    @PostMapping("/create")
    @Operation(summary = "Create a file record")
    fun createFile(@Valid @RequestBody createReqVO: FileCreateReqVO): CommonResult<Long> =
        CommonResult.success(fileService.createFile(createReqVO))
}
