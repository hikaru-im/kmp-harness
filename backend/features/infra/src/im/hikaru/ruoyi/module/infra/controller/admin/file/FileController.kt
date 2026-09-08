package im.hikaru.ruoyi.module.infra.controller.admin.file

import im.hikaru.contracts.common.PageResponse
import im.hikaru.contracts.infra.FileInfo
import im.hikaru.contracts.infra.FilePresignedUrl
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.*
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileDO
import im.hikaru.ruoyi.module.infra.service.file.FileService
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FileTypeUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File

@Tag(name = "管理后台 - 文件存储")
@RestController
@RequestMapping("/infra/file")
@Validated
class FileController(
    private val fileService: FileService,
) {

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "模式一：后端上传文件")
    @Parameter(name = "file", description = "文件附件", required = true, schema = Schema(type = "string", format = "binary"))
    @Throws(Exception::class)
    fun uploadFile(@Valid uploadReqVO: FileUploadReqVO): CommonResult<String> {
        val file: MultipartFile = uploadReqVO.file!!
        val content = file.inputStream.use { it.readBytes() }
        return CommonResult.success(
            fileService.createFile(content, file.originalFilename, uploadReqVO.directory, file.contentType),
        )
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "获取文件预签名地址（上传）")
    @Parameters(
        Parameter(name = "name", description = "文件名称", required = true),
        Parameter(name = "directory", description = "文件目录"),
    )
    fun getFilePresignedUrl(
        @RequestParam("name") name: String,
        @RequestParam(value = "directory", required = false) directory: String?,
    ): CommonResult<FilePresignedUrl> = CommonResult.success(fileService.presignPutUrl(name, directory).toContract())

    @PostMapping("/create")
    @Operation(summary = "创建文件")
    fun createFile(@Valid @RequestBody createReqVO: FileCreateReqVO): CommonResult<Long> =
        CommonResult.success(fileService.createFile(createReqVO))

    @GetMapping("/get")
    @Operation(summary = "获得文件")
    @Parameter(name = "id", description = "编号", required = true)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file:query')")
    fun getFile(@RequestParam("id") id: Long): CommonResult<FileInfo?> = CommonResult.success(
        BeanUtils.toBean(fileService.getFile(id), FileRespVO::class.java)?.toContract(),
    )

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件")
    @Parameter(name = "id", description = "编号", required = true)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file:delete')")
    @Throws(Exception::class)
    fun deleteFile(@RequestParam("id") id: Long): CommonResult<Boolean> {
        fileService.deleteFile(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除文件")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file:delete')")
    @Throws(Exception::class)
    fun deleteFileList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        fileService.deleteFileList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/{configId}/get/**")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "下载文件")
    @Parameter(name = "configId", description = "配置编号", required = true)
    @Throws(Exception::class)
    fun getFileContent(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @PathVariable("configId") configId: Long,
    ) {
        // 获取请求的路径
        var path = request.requestURI.substringAfterLast("/get/")
        if (path.isEmpty()) {
            throw IllegalArgumentException("结尾的 path 路径必须传递")
        }
        path = HttpUtils.decodeUrlPath(path)
        // 读取内容
        val content = fileService.getFileContent(configId, path)
        if (content == null) {
            log.warn("[getFileContent][configId({}) path({}) 文件不存在]", configId, path)
            response.status = HttpStatus.NOT_FOUND.value()
            return
        }
        val file = fileService.getFileByConfigIdAndPath(configId, path)
        val filename = if (file?.name?.isNotEmpty() == true) file.name else File(path).name
        // writeAttachment 简化实现
        FileTypeUtils.writeAttachment(response, filename, content)
    }

    @GetMapping("/page")
    @Operation(summary = "获得文件分页")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file:query')")
    fun getFilePage(@Valid pageVO: FilePageReqVO): CommonResult<PageResponse<FileInfo>> {
        val pageResult = fileService.getFilePage(pageVO)
        val response = BeanUtils.toBean(pageResult, FileRespVO::class.java)!!
        return CommonResult.success(
            PageResponse(
                total = response.total,
                list = response.list.map(FileRespVO::toContract),
            ),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileController::class.java)
    }
}
