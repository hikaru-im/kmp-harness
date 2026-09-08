package im.hikaru.ruoyi.module.system.controller.admin.dict

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataRespVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictDataDO
import im.hikaru.ruoyi.module.system.service.dict.DictDataService
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

@Tag(name = "Admin - Dictionary data")
@RestController
@RequestMapping("/system/dict-data")
@Validated
class DictDataController(
    private val service: DictDataService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create dictionary data")
    @PreAuthorize("@ss.hasPermission('system:dict:create')")
    fun create(@Valid @RequestBody req: DictDataSaveReqVO): CommonResult<Long> =
        CommonResult.success(service.createDictData(req))

    @PutMapping("/update")
    @Operation(summary = "Update dictionary data")
    @PreAuthorize("@ss.hasPermission('system:dict:update')")
    fun update(@Valid @RequestBody req: DictDataSaveReqVO): CommonResult<Boolean> {
        service.updateDictData(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        service.deleteDictData(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        service.deleteDictDataList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/list-all-simple", "/simple-list")
    fun simpleList(): CommonResult<List<DictDataSimpleRespVO>> = CommonResult.success(
        service.getDictDataList(CommonStatusEnum.ENABLE.status, null).map { it.toSimpleResp() },
    )

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    fun page(@Valid req: DictDataPageReqVO): CommonResult<PageResult<DictDataRespVO>> =
        CommonResult.success(service.getDictDataPage(req).toRespPage())

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<DictDataRespVO?> =
        CommonResult.success(service.getDictData(id)?.toResp())

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:dict:export')")
    fun export(response: HttpServletResponse, req: DictDataPageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        ExcelUtils.write(response, "dictionary-data.xls", "data", DictDataRespVO::class.java, service.getDictDataPage(req).list.map { it.toResp() })
    }

    private fun DictDataDO.toResp() = DictDataRespVO().apply {
        id = this@toResp.id
        sort = this@toResp.sort
        label = this@toResp.label
        value = this@toResp.value
        dictType = this@toResp.dictType
        status = this@toResp.status
        colorType = this@toResp.colorType
        cssClass = this@toResp.cssClass
        remark = this@toResp.remark
        createTime = this@toResp.createTime?.toJavaLocalDateTime()
    }

    private fun DictDataDO.toSimpleResp() = DictDataSimpleRespVO().apply {
        dictType = this@toSimpleResp.dictType
        value = this@toSimpleResp.value
        label = this@toSimpleResp.label
        colorType = this@toSimpleResp.colorType
        cssClass = this@toSimpleResp.cssClass
    }

    private fun PageResult<DictDataDO>.toRespPage() =
        PageResult(total = total ?: 0L, list = list.map { it.toResp() })
}
