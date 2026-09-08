package im.hikaru.ruoyi.framework.common.enums

/**
 * 文档地址
 *
 * @author 芋道源码
 */
enum class DocumentEnum(
    val url: String,
    val memo: String,
) {
    REDIS_INSTALL("https://gitee.com/zhijiantianya/ruoyi-vue-pro/issues/I4VCSJ", "Redis 安装文档"),
    TENANT("https://doc.iocoder.cn", "SaaS 多租户文档"),
}
