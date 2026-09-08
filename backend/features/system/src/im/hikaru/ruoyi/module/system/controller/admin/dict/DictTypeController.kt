package im.hikaru.ruoyi.module.system.controller.admin.dict

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypeRespVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypeSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypeSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictTypeDO
import im.hikaru.ruoyi.module.system.service.dict.DictTypeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
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

@Tag(name = "Admin - Dictionary type")
@RestController
@RequestMapping("/system/dict-type")
@Validated
class DictTypeController(
    private val service: DictTypeService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create dictionary type")
    @PreAuthorize("@ss.hasPermission('system:dict:create')")
    fun create(@Valid @RequestBody req: DictTypeSaveReqVO): CommonResult<Long> =
        CommonResult.success(service.createDictType(req))

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('system:dict:update')")
    fun update(@Valid @RequestBody req: DictTypeSaveReqVO): CommonResult<Boolean> {
        service.updateDictType(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        service.deleteDictType(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        service.deleteDictTypeList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    fun page(@Valid req: DictTypePageReqVO): CommonResult<PageResult<DictTypeRespVO>> =
        CommonResult.success(service.getDictTypePage(req).toRespPage())

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<DictTypeRespVO?> =
        CommonResult.success(service.getDictType(id)?.toResp())

    @GetMapping("/list-all-simple", "/simple-list")
    fun simpleList(): CommonResult<List<DictTypeSimpleRespVO>> = CommonResult.success(
        service.getDictTypeList().map { DictTypeSimpleRespVO().apply { id = it.id; name = it.name; type = it.type } },
    )

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    fun export(response: HttpServletResponse, req: DictTypePageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        ExcelUtils.write(response, "dictionary-types.xls", "data", DictTypeRespVO::class.java, service.getDictTypePage(req).list.map { it.toResp() })
    }

    private fun DictTypeDO.toResp() = DictTypeRespVO().apply {
        id = this@toResp.id
        name = this@toResp.name
        type = this@toResp.type
        status = this@toResp.status
        remark = this@toResp.remark
        createTime = this@toResp.createTime?.toJavaLocalDateTime()
    }

    private fun PageResult<DictTypeDO>.toRespPage() =
        PageResult(total = total ?: 0L, list = list.map { it.toResp() })
}
