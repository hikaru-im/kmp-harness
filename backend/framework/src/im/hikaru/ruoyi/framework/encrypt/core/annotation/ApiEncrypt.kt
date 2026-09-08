package im.hikaru.ruoyi.framework.encrypt.core.annotation

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ApiEncrypt(
    val request: Boolean = true,
    val response: Boolean = true,
)
