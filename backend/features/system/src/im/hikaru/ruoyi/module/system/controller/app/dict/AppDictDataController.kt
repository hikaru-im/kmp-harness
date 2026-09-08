package im.hikaru.ruoyi.module.system.controller.app.dict

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.system.controller.app.dict.vo.AppDictDataRespVO
import im.hikaru.ruoyi.module.system.service.dict.DictDataService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Dictionary data")
@RestController
@RequestMapping("/system/dict-data")
@Validated
class AppDictDataController(
    private val service: DictDataService,
) {
    @GetMapping("/type")
    @Operation(summary = "Get dictionary data by type")
    @Parameter(name = "type", required = true)
    @PermitAll
    fun getByType(@RequestParam("type") type: String): CommonResult<List<AppDictDataRespVO>> =
        CommonResult.success(
            service.getDictDataList(CommonStatusEnum.ENABLE.status, type).map {
                AppDictDataRespVO(it.id, it.label, it.value, it.dictType)
            },
        )
}
