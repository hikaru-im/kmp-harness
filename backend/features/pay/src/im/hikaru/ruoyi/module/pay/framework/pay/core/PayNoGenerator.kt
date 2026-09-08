package im.hikaru.ruoyi.module.pay.framework.pay.core

import java.util.UUID

/** Generates compact, collision-resistant merchant-facing numbers. */
object PayNoGenerator {
    fun next(prefix: String): String = prefix + UUID.randomUUID().toString().replace("-", "").take(24)
}
