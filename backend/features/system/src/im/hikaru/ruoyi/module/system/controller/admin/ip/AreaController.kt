package im.hikaru.ruoyi.module.system.controller.admin.ip

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.ip.core.Area
import im.hikaru.ruoyi.framework.ip.core.utils.AreaUtils
import im.hikaru.ruoyi.framework.ip.core.utils.IPUtils
import im.hikaru.ruoyi.module.system.controller.admin.ip.vo.AreaNodeRespVO
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/system/area")
@Validated
class AreaController {
    @GetMapping("/tree")
    fun getAreaTree(): CommonResult<List<AreaNodeRespVO>> {
        val china = requireNotNull(AreaUtils.getArea(Area.ID_CHINA)) { "China area data is unavailable" }
        return CommonResult.success(china.children.map { it.toAdminResponse() })
    }

    @GetMapping("/get-by-ip")
    fun getAreaByIp(@RequestParam("ip") ip: String): CommonResult<String> {
        val area = IPUtils.getArea(ip) ?: return CommonResult.success("Unknown")
        return CommonResult.success(AreaUtils.format(area.id).orEmpty())
    }

    private fun Area.toAdminResponse(): AreaNodeRespVO = AreaNodeRespVO().also { response ->
        response.id = id
        response.name = name
        response.children = children.map { it.toAdminResponse() }
    }
}
