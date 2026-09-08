package im.hikaru.ruoyi.server.controller

import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_IMPLEMENTED
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DefaultController {
    private val log = LoggerFactory.getLogger(javaClass)

    @RequestMapping("/admin-api/bpm/**")
    fun bpm404(): CommonResult<Boolean> = disabled("Workflow", "yudao-module-bpm", "https://doc.iocoder.cn/bpm/")

    @RequestMapping("/admin-api/product/**", "/admin-api/trade/**", "/admin-api/promotion/**")
    fun mall404(): CommonResult<Boolean> = disabled("Mall", "yudao-module-mall", "https://doc.iocoder.cn/mall/build/")

    @RequestMapping("/admin-api/erp/**")
    fun erp404(): CommonResult<Boolean> = disabled("ERP", "yudao-module-erp", "https://doc.iocoder.cn/erp/build/")

    @RequestMapping("/admin-api/wms/**")
    fun wms404(): CommonResult<Boolean> = disabled("WMS", "yudao-module-wms", "https://doc.iocoder.cn/wms/build/")

    @RequestMapping("/admin-api/crm/**")
    fun crm404(): CommonResult<Boolean> = disabled("CRM", "yudao-module-crm", "https://doc.iocoder.cn/crm/build/")

    @RequestMapping("/admin-api/mes/**")
    fun mes404(): CommonResult<Boolean> = disabled("MES", "yudao-module-mes", "https://doc.iocoder.cn/mes/build/")

    @RequestMapping("/admin-api/im/**")
    fun im404(): CommonResult<Boolean> = disabled("Instant messaging", "yudao-module-im", "https://doc.iocoder.cn/im/build/")

    @RequestMapping("/admin-api/report/**")
    fun report404(): CommonResult<Boolean> = disabled("Reporting", "yudao-module-report", "https://doc.iocoder.cn/report/")

    @RequestMapping("/admin-api/ai/**")
    fun ai404(): CommonResult<Boolean> = disabled("AI", "yudao-module-ai", "https://doc.iocoder.cn/ai/build/")

    @RequestMapping("/admin-api/iot/**")
    fun iot404(): CommonResult<Boolean> = disabled("IoT", "yudao-module-iot", "https://doc.iocoder.cn/iot/build/")

    @RequestMapping("/test")
    @PermitAll
    fun test(request: HttpServletRequest): CommonResult<Boolean> {
        log.info("Query: {}", ServletUtils.getParamMap(request))
        log.info("Header: {}", ServletUtils.getHeaderMap(request))
        log.info("Body: {}", ServletUtils.getBody(request))
        return CommonResult.success(true)
    }

    private fun disabled(name: String, module: String, documentation: String): CommonResult<Boolean> =
        CommonResult.error(NOT_IMPLEMENTED.code, "[$name module $module is disabled][See $documentation to enable it]")
}
