package im.hikaru.ruoyi.framework.apilog.core.annotation

import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiAccessLog(
    val enable: Boolean = true,
    val requestEnable: Boolean = true,
    val responseEnable: Boolean = false,
    val sanitizeKeys: Array<String> = [],
    val operateModule: String = "",
    val operateName: String = "",
    val operateType: Array<OperateTypeEnum> = [],
)
