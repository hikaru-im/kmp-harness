package im.hikaru.ruoyi.module.system.enums.permission

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class DataScopeEnum(val scope: Int) : ArrayValuable<Int> {
    ALL(1),
    DEPT_CUSTOM(2),
    DEPT_ONLY(3),
    DEPT_AND_CHILD(4),
    SELF(5);

    override fun array(): Array<Int> = ARRAYS

    companion object {
        val ARRAYS: Array<Int> = entries.map { it.scope }.toTypedArray()
    }
}
