package im.hikaru.ruoyi.module.pay.test

import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import org.springframework.context.ApplicationContext

internal inline fun <reified T : Any> interfaceProxy(
    crossinline handler: (String, Array<out Any?>?) -> Any?,
): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { proxy, method, args ->
    when (method.name) {
        "toString" -> "${T::class.java.simpleName}TestDouble"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> handler(method.name, args) ?: primitiveDefault(method.returnType)
    }
} as T

internal fun selfApplicationContext(reference: AtomicReference<Any>): ApplicationContext =
    interfaceProxy { method, _ ->
        when (method) {
            "getBean" -> reference.get()
            else -> null
        }
    }

private fun primitiveDefault(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE -> 0.toByte()
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0F
    java.lang.Double.TYPE -> 0.0
    java.lang.Character.TYPE -> '\u0000'
    else -> null
}
