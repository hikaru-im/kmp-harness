package im.hikaru.ruoyi.framework.desensitize.core.base.serializer

import im.hikaru.ruoyi.framework.desensitize.core.base.annotation.DesensitizeBy
import im.hikaru.ruoyi.framework.desensitize.core.base.handler.DesensitizationHandler
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.ser.std.StdSerializer
import java.util.concurrent.ConcurrentHashMap

/** Jackson serializer selected by composed desensitization annotations. */
class StringDesensitizeSerializer private constructor(
    private val handler: DesensitizationHandler<Annotation>?,
    private val annotation: Annotation?,
) : StdSerializer<String>(String::class.java) {

    constructor() : this(null, null)

    override fun createContextual(
        ctxt: SerializationContext,
        property: BeanProperty?,
    ): ValueSerializer<*> {
        val member = property?.member ?: return this
        val annotation = member.annotations()
            .filter { it.annotationClass.java.isAnnotationPresent(DesensitizeBy::class.java) }
            .findFirst()
            .orElse(null)
            ?: return this
        val desensitizeBy = annotation.annotationClass.java.getAnnotation(DesensitizeBy::class.java)
            ?: return this
        val handler = handlers.computeIfAbsent(desensitizeBy.handler.java) { handlerType ->
            handlerType.getDeclaredConstructor().newInstance() as DesensitizationHandler<*>
        }
        @Suppress("UNCHECKED_CAST")
        return StringDesensitizeSerializer(handler as DesensitizationHandler<Annotation>, annotation)
    }

    @Throws(JacksonException::class)
    override fun serialize(value: String, gen: JsonGenerator, ctxt: SerializationContext) {
        if (value.isBlank()) {
            gen.writeNull()
            return
        }
        val result = if (handler != null && annotation != null) {
            handler.desensitize(value, annotation)
        } else {
            value
        }
        gen.writeString(result)
    }

    companion object {
        private val handlers = ConcurrentHashMap<Class<*>, DesensitizationHandler<*>>()
    }
}
