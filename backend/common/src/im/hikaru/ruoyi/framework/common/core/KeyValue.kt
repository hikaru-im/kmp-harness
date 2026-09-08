package im.hikaru.ruoyi.framework.common.core

import java.io.Serializable

/**
 * Key Value 的键值对
 *
 * @author 芋道源码
 */
data class KeyValue<K, V>(
    var key: K? = null,
    var value: V? = null,
) : Serializable
