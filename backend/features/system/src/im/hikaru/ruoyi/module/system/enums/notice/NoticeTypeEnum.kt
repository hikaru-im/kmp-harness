package im.hikaru.ruoyi.module.system.enums.notice

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class NoticeTypeEnum(val type: Int) : ArrayValuable<Int> {
    NOTICE(1),
    ANNOUNCEMENT(2),
    ;

    override fun array(): Array<Int> = ARRAYS

    companion object {
        val ARRAYS: Array<Int> = entries.map { it.type }.toTypedArray()
    }
}
