package im.hikaru.ruoyi.module.infra.framework.codegen.config

import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenFrontTypeEnum
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenVOTypeEnum
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "yudao.codegen")
data class CodegenProperties(
    var basePackage: String = "im.hikaru.ruoyi",
    var dbSchemas: List<String> = emptyList(),
    var frontType: Int = CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.type,
    var voType: Int = CodegenVOTypeEnum.VO.type,
    var deleteBatchEnable: Boolean = true,
    var unitTestEnable: Boolean = false,
    var importEnable: Boolean = false,
)
