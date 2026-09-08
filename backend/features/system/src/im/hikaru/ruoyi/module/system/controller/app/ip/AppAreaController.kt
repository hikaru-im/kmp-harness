package im.hikaru.ruoyi.module.system.controller.app.ip

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.ip.core.Area
import im.hikaru.ruoyi.framework.ip.core.utils.AreaUtils
import im.hikaru.ruoyi.module.system.controller.app.ip.vo.AppAreaNodeRespVO
import jakarta.annotation.security.PermitAll
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/system/area")
@Validated
class AppAreaController {
    @GetMapping("/tree")
    @PermitAll
    fun getAreaTree(): CommonResult<List<AppAreaNodeRespVO>> {
        val china = requireNotNull(AreaUtils.getArea(Area.ID_CHINA)) { "China area data is unavailable" }
        return CommonResult.success(china.children.map { it.toAppResponse() })
    }

    private fun Area.toAppResponse(): AppAreaNodeRespVO = AppAreaNodeRespVO().also { response ->
        response.id = id
        response.name = name
        response.children = children.map { it.toAppResponse() }
    }
}
