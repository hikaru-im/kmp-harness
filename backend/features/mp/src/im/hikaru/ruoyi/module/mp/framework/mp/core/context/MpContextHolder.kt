package im.hikaru.ruoyi.module.mp.framework.mp.core.context

object MpContextHolder {
    private val appIdHolder = ThreadLocal<String>()

    fun setAppId(appId: String) = appIdHolder.set(appId)
    fun getAppId(): String = requireNotNull(appIdHolder.get()) { "WeChat appId context is not available" }
    fun clear() = appIdHolder.remove()
}
