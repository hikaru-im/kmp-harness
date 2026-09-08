package im.hikaru.ruoyi.framework.common.core

/**
 * 可生成 T 数组的接口
 *
 * @author HUIHUI
 */
interface ArrayValuable<T> {
    /**
     * @return 数组
     */
    fun array(): Array<T>
}
