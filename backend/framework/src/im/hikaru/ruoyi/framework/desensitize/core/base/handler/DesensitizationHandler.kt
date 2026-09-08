package im.hikaru.ruoyi.framework.desensitize.core.base.handler

/** Transforms a string according to a desensitization annotation. */
interface DesensitizationHandler<A : Annotation> {

    fun desensitize(origin: String, annotation: A): String

    fun getDisable(annotation: A): String = try {
        annotation.annotationClass.java.methods
            .firstOrNull { it.name == "disable" && it.parameterCount == 0 }
            ?.invoke(annotation) as? String ?: ""
    } catch (_: ReflectiveOperationException) {
        ""
    }
}
