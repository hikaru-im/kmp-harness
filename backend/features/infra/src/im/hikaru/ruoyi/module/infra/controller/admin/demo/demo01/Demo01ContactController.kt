package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.demo.demo01.vo.Demo01ContactSaveReqVO
import im.hikaru.ruoyi.module.infra.service.demo.demo01.Demo01ContactService
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

@Tag(name = "Admin - Demo contact")
@RestController
@RequestMapping("/infra/demo01-contact")
@Validated
class Demo01ContactController(
    private val demo01ContactService: Demo01ContactService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create demo contact")
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:create')")
    fun create(@Valid @RequestBody req: Demo01ContactSaveReqVO): CommonResult<Long> =
        CommonResult.success(demo01ContactService.createDemo01Contact(req))

    @PutMapping("/update")
    @Operation(summary = "Update demo contact")
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:update')")
    fun update(@Valid @RequestBody req: Demo01ContactSaveReqVO): CommonResult<Boolean> {
        demo01ContactService.updateDemo01Contact(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete demo contact")
    @Parameter(name = "id", description = "Contact id", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        demo01ContactService.deleteDemo01Contact(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete demo contacts")
    @Parameter(name = "ids", description = "Contact ids", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        demo01ContactService.deleteDemo01ContactList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get demo contact")
    @Parameter(name = "id", description = "Contact id", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<Demo01ContactRespVO?> =
        CommonResult.success(BeanUtils.toBean(demo01ContactService.getDemo01Contact(id), Demo01ContactRespVO::class.java))

    @GetMapping("/page")
    @Operation(summary = "Page demo contacts")
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:query')")
    fun page(@Valid req: Demo01ContactPageReqVO): CommonResult<PageResult<Demo01ContactRespVO>> =
        CommonResult.success(
            BeanUtils.toBean(demo01ContactService.getDemo01ContactPage(req), Demo01ContactRespVO::class.java)!!,
        )

    @GetMapping("/export-excel")
    @Operation(summary = "Export demo contacts")
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:export')")
    fun export(req: Demo01ContactPageReqVO, response: HttpServletResponse) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val list = demo01ContactService.getDemo01ContactPage(req).list
        ExcelUtils.write(
            response,
            "demo-contacts.xls",
            "data",
            Demo01ContactRespVO::class.java,
            BeanUtils.toBean(list, Demo01ContactRespVO::class.java) ?: emptyList(),
        )
    }
}
