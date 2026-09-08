package im.hikaru.ruoyi.module.infra.controller.admin.codegen

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenCreateListReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenDetailRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenPreviewRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenUpdateReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.column.CodegenColumnRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTablePageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTableRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.DatabaseTableRespVO
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FileTypeUtils
import im.hikaru.ruoyi.module.infra.service.codegen.CodegenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Tag(name = "Admin - Code Generator")
@RestController
@RequestMapping("/infra/codegen")
@Validated
class CodegenController(
    private val codegenService: CodegenService,
) {

    @GetMapping("/db/table/list")
    @Operation(summary = "List database tables that have not been imported")
    @PreAuthorize("@ss.hasPermission('infra:codegen:query')")
    fun getDatabaseTableList(
        @RequestParam dataSourceConfigId: Long,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) comment: String?,
    ): CommonResult<List<DatabaseTableRespVO>> =
        CommonResult.success(codegenService.getDatabaseTableList(dataSourceConfigId, name, comment))

    @GetMapping("/table/list")
    @PreAuthorize("@ss.hasPermission('infra:codegen:query')")
    fun getCodegenTableList(@RequestParam dataSourceConfigId: Long): CommonResult<List<CodegenTableRespVO>> =
        CommonResult.success(
            requireNotNull(
                BeanUtils.toBean(codegenService.getCodegenTableList(dataSourceConfigId), CodegenTableRespVO::class.java),
            ),
        )

    @GetMapping("/table/page")
    @PreAuthorize("@ss.hasPermission('infra:codegen:query')")
    fun getCodegenTablePage(@Valid pageReqVO: CodegenTablePageReqVO): CommonResult<PageResult<CodegenTableRespVO>> =
        CommonResult.success(
            requireNotNull(BeanUtils.toBean(codegenService.getCodegenTablePage(pageReqVO), CodegenTableRespVO::class.java)),
        )

    @GetMapping("/detail")
    @Parameter(name = "tableId", required = true)
    @PreAuthorize("@ss.hasPermission('infra:codegen:query')")
    fun getCodegenDetail(@RequestParam tableId: Long): CommonResult<CodegenDetailRespVO> {
        val response = CodegenDetailRespVO().apply {
            table = BeanUtils.toBean(codegenService.getCodegenTable(tableId), CodegenTableRespVO::class.java)
            columns = BeanUtils.toBean(
                codegenService.getCodegenColumnListByTableId(tableId),
                CodegenColumnRespVO::class.java,
            )
        }
        return CommonResult.success(response)
    }

    @PostMapping("/create-list")
    @PreAuthorize("@ss.hasPermission('infra:codegen:create')")
    fun createCodegenList(@Valid @RequestBody reqVO: CodegenCreateListReqVO): CommonResult<List<Long>> =
        CommonResult.success(
            codegenService.createCodegenList(SecurityFrameworkUtils.getLoginUserNickname() ?: "system", reqVO),
        )

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('infra:codegen:update')")
    fun updateCodegen(@Valid @RequestBody updateReqVO: CodegenUpdateReqVO): CommonResult<Boolean> {
        codegenService.updateCodegen(updateReqVO)
        return CommonResult.success(true)
    }

    @PutMapping("/sync-from-db")
    @PreAuthorize("@ss.hasPermission('infra:codegen:update')")
    fun syncCodegenFromDB(@RequestParam tableId: Long): CommonResult<Boolean> {
        codegenService.syncCodegenFromDB(tableId)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('infra:codegen:delete')")
    fun deleteCodegen(@RequestParam tableId: Long): CommonResult<Boolean> {
        codegenService.deleteCodegen(tableId)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @PreAuthorize("@ss.hasPermission('infra:codegen:delete')")
    fun deleteCodegenList(@RequestParam tableIds: List<Long>): CommonResult<Boolean> {
        codegenService.deleteCodegenList(tableIds)
        return CommonResult.success(true)
    }

    @GetMapping("/preview")
    @PreAuthorize("@ss.hasPermission('infra:codegen:preview')")
    fun previewCodegen(@RequestParam tableId: Long): CommonResult<List<CodegenPreviewRespVO>> =
        CommonResult.success(
            codegenService.generationCodes(tableId).map { (path, code) ->
                CodegenPreviewRespVO().apply { filePath = path; this.code = code }
            },
        )

    @GetMapping("/download")
    @PreAuthorize("@ss.hasPermission('infra:codegen:download')")
    fun downloadCodegen(@RequestParam tableId: Long, response: HttpServletResponse) {
        FileTypeUtils.writeAttachment(response, "codegen.zip", zip(codegenService.generationCodes(tableId)))
    }

    private fun zip(codes: Map<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
            codes.forEach { (path, code) ->
                require(!path.startsWith('/') && !path.contains("..")) { "Invalid generated path: $path" }
                zip.putNextEntry(ZipEntry(path.replace('\\', '/')))
                zip.write(code.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }
}
