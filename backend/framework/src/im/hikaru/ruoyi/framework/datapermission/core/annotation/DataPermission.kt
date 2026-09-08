package im.hikaru.ruoyi.framework.datapermission.core.annotation

import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRule
import kotlin.reflect.KClass

/**
 * 数据权限注解
 * 可声明在类或者方法上，标识使用的数据权限规则
 *
 * 迁移说明：Java 注解 → Kotlin 注解；`Class<? extends DataPermissionRule>[]` → `Array<KClass<out DataPermissionRule>>`。
 * 注意：为保证 [im.hikaru.ruoyi.framework.datapermission.core.util.DataPermissionUtils] 通过反射拿到
 * `enable = false` 的占位注解，本注解未使用 `@MustBeDocumented` 之外的特殊约束。
 *
 * @author 芋道源码
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class DataPermission(

    /**
     * 当前类或方法是否开启数据权限
     *
     * 即使不添加 @DataPermission 注解，默认是开启状态，可通过设置 enable 为 false 禁用
     */
    val enable: Boolean = true,

    /**
     * 生效的数据权限规则数组，优先级高于 [excludeRules]
     */
    val includeRules: Array<KClass<out DataPermissionRule>> = [],

    /**
     * 排除的数据权限规则数组，优先级最低
     */
    val excludeRules: Array<KClass<out DataPermissionRule>> = [],
)
