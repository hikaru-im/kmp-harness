package im.hikaru.ruoyi.framework.xss.core.clean

fun interface XssCleaner {
    fun clean(html: String): String
}
