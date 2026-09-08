package im.hikaru.ruoyi.framework.dict.config

import im.hikaru.ruoyi.framework.common.biz.system.dict.DictDataCommonApi
import im.hikaru.ruoyi.framework.dict.core.DictFrameworkUtils
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration
class YudaoDictAutoConfiguration {

    @Bean
    @Suppress("InstantiationOfUtilityClass")
    fun dictUtils(dictDataApi: DictDataCommonApi): DictFrameworkUtils {
        DictFrameworkUtils.init(dictDataApi)
        return DictFrameworkUtils()
    }
}
