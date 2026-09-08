package im.hikaru.ruoyi.module.system.enums.notify

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class NotifyTemplateTypeEnum(val type: Int) : ArrayValuable<Int> {
    SYSTEM_MESSAGE(2),
    NOTIFICATION_MESSAGE(1),
    ;

    override fun array(): Array<Int> = ARRAYS

    companion object {
        val ARRAYS: Array<Int> = entries.map { it.type }.toTypedArray()
    }
}
