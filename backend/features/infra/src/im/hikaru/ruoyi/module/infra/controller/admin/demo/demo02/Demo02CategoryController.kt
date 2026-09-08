package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategoryListReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategoryRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo02.vo.Demo02CategorySaveReqVO
import im.hikaru.ruoyi.module.infra.service.demo.demo02.Demo02CategoryService
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

@Tag(name = "Admin - Demo category")
@RestController
@RequestMapping("/infra/demo02-category")
@Validated
class Demo02CategoryController(
    private val demo02CategoryService: Demo02CategoryService,
) {

    @PostMapping("/create")
    @Operation(summary = "Create demo category")
    @PreAuthorize("@ss.hasPermission('infra:demo02-category:create')")
    fun create(@Valid @RequestBody req: Demo02CategorySaveReqVO): CommonResult<Long> =
        CommonResult.success(demo02CategoryService.createDemo02Category(req))

    @PutMapping("/update")
    @Operation(summary = "Update demo category")
    @PreAuthorize("@ss.hasPermission('infra:demo02-category:update')")
    fun update(@Valid @RequestBody req: Demo02CategorySaveReqVO): CommonResult<Boolean> {
        demo02CategoryService.updateDemo02Category(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete demo category")
    @Parameter(name = "id", description = "Category id", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo02-category:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        demo02CategoryService.deleteDemo02Category(id)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get demo category")
    @Parameter(name = "id", description = "Category id", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo02-category:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<Demo02CategoryRespVO?> =
        CommonResult.success(BeanUtils.toBean(demo02CategoryService.getDemo02Category(id), Demo02CategoryRespVO::class.java))

    @GetMapping("/list")
    @Operation(summary = "List demo categories")
    @PreAuthorize("@ss.hasPermission('infra:demo02-category:query')")
    fun list(@Valid req: Demo02CategoryListReqVO): CommonResult<List<Demo02CategoryRespVO>> =
        CommonResult.success(
            BeanUtils.toBean(demo02CategoryService.getDemo02CategoryList(req), Demo02CategoryRespVO::class.java)
                ?: emptyList(),
        )

    @GetMapping("/export-excel")
    @Operation(summary = "Export demo categories")
    @PreAuthorize("@ss.hasPermission('infra:demo02-category:export')")
    fun export(req: Demo02CategoryListReqVO, response: HttpServletResponse) {
        val list = demo02CategoryService.getDemo02CategoryList(req)
        ExcelUtils.write(
            response,
            "demo-categories.xls",
            "data",
            Demo02CategoryRespVO::class.java,
            BeanUtils.toBean(list, Demo02CategoryRespVO::class.java) ?: emptyList(),
        )
    }
}
