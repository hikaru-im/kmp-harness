package im.hikaru.ruoyi.framework.banner.core

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.util.ClassUtils
import java.util.concurrent.CompletableFuture

/** Prints documentation links and optional module hints after startup. */
class BannerApplicationRunner : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        CompletableFuture.runAsync {
            Thread.sleep(1_000)
            logger.info(
                """

                ----------------------------------------------------------
                项目启动成功！
                接口文档: https://doc.iocoder.cn/api-doc/
                开发文档: https://doc.iocoder.cn
                视频教程: https://t.zsxq.com/02Yf6M7Qn
                ----------------------------------------------------------
                """.trimIndent(),
            )
            optionalModules
                .filter { (className, _) -> isNotPresent(className) }
                .forEach { (_, message) -> println(message) }
        }
    }

    private fun isNotPresent(className: String): Boolean =
        !ClassUtils.isPresent(className, ClassUtils.getDefaultClassLoader())

    companion object {
        private val logger = LoggerFactory.getLogger(BannerApplicationRunner::class.java)

        private val optionalModules = listOf(
            "im.hikaru.ruoyi.module.report.framework.security.config.SecurityConfiguration" to
                "[报表模块 yudao-module-report - 已禁用][参考 https://doc.iocoder.cn/report/ 开启]",
            "im.hikaru.ruoyi.module.bpm.framework.flowable.config.BpmFlowableConfiguration" to
                "[工作流模块 yudao-module-bpm - 已禁用][参考 https://doc.iocoder.cn/bpm/ 开启]",
            "im.hikaru.ruoyi.module.trade.framework.web.config.TradeWebConfiguration" to
                "[商城系统 yudao-module-mall - 已禁用][参考 https://doc.iocoder.cn/mall/build/ 开启]",
            "im.hikaru.ruoyi.module.erp.framework.web.config.ErpWebConfiguration" to
                "[ERP 系统 yudao-module-erp - 已禁用][参考 https://doc.iocoder.cn/erp/build/ 开启]",
            "im.hikaru.ruoyi.module.wms.framework.web.config.WmsWebConfiguration" to
                "[WMS 仓库管理系统 yudao-module-wms - 已禁用][参考 https://doc.iocoder.cn/wms/build/ 开启]",
            "im.hikaru.ruoyi.module.crm.framework.web.config.CrmWebConfiguration" to
                "[CRM 系统 yudao-module-crm - 已禁用][参考 https://doc.iocoder.cn/crm/build/ 开启]",
            "im.hikaru.ruoyi.module.mes.framework.web.config.MesWebConfiguration" to
                "[MES 系统 yudao-module-mes - 已禁用][参考 https://doc.iocoder.cn/mes/build/ 开启]",
            "im.hikaru.ruoyi.module.mp.framework.mp.config.MpConfiguration" to
                "[微信公众号 yudao-module-mp - 已禁用][参考 https://doc.iocoder.cn/mp/build/ 开启]",
            "im.hikaru.ruoyi.module.pay.framework.web.config.PayWebConfiguration" to
                "[支付系统 yudao-module-pay - 已禁用][参考 https://doc.iocoder.cn/pay/build/ 开启]",
            "im.hikaru.ruoyi.module.ai.framework.web.config.AiWebConfiguration" to
                "[AI 大模型 yudao-module-ai - 已禁用][参考 https://doc.iocoder.cn/ai/build/ 开启]",
            "im.hikaru.ruoyi.module.iot.framework.web.config.IotWebConfiguration" to
                "[IoT 物联网 yudao-module-iot - 已禁用][参考 https://doc.iocoder.cn/iot/build/ 开启]",
            "im.hikaru.ruoyi.module.im.framework.web.config.ImWebConfiguration" to
                "[IM 即时通讯 yudao-module-im - 已禁用][参考 https://doc.iocoder.cn/im/build/ 开启]",
        )
    }
}
