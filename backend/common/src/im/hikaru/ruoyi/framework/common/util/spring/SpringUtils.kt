package im.hikaru.ruoyi.framework.common.util.spring

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class SpringUtils : ApplicationContextAware {
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        context = applicationContext
    }

    companion object {
        @Volatile private var context: ApplicationContext? = null
        @JvmStatic fun <T : Any> getBean(type: Class<T>): T = requireContext().getBeanProvider(type).getObject()
        @JvmStatic fun getBean(name: String): Any = requireContext().getBean(name)
        @JvmStatic fun getActiveProfile(): String? = requireContext().environment.activeProfiles.firstOrNull()
        @JvmStatic fun isProd(): Boolean = getActiveProfile() == "prod"
        @JvmStatic fun getProperty(key: String): String? = requireContext().environment.getProperty(key)
        private fun requireContext(): ApplicationContext = context
            ?: throw object : BeansException("Spring application context is not initialized") {}
    }
}
