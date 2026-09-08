package im.hikaru.ruoyi.module.system.service

import im.hikaru.ruoyi.module.system.service.dept.DeptServiceImpl
import im.hikaru.ruoyi.module.system.service.mail.MailAccountServiceImpl
import im.hikaru.ruoyi.module.system.service.mail.MailTemplateServiceImpl
import im.hikaru.ruoyi.module.system.service.notify.NotifyTemplateServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientServiceImpl
import im.hikaru.ruoyi.module.system.service.sms.SmsTemplateServiceImpl
import im.hikaru.ruoyi.module.system.service.social.SocialClientServiceImpl
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable

class SystemCacheAnnotationParityTest {
    @Test
    fun `reference cache reads remain cacheable`() {
        val methods = listOf(
            DeptServiceImpl::class.java to "getChildDeptIdListFromCache",
            OAuth2ClientServiceImpl::class.java to "getOAuth2ClientFromCache",
            MailAccountServiceImpl::class.java to "getMailAccountFromCache",
            MailTemplateServiceImpl::class.java to "getMailTemplateByCodeFromCache",
            NotifyTemplateServiceImpl::class.java to "getNotifyTemplateByCodeFromCache",
            SmsTemplateServiceImpl::class.java to "getSmsTemplateByCodeFromCache",
            SocialClientServiceImpl::class.java to "getSubscribeTemplateList",
        )
        methods.forEach { (type, name) -> assertAnnotated(type, name, Cacheable::class.java) }
    }

    @Test
    fun `reference cache mutations retain eviction`() {
        val methods = listOf(
            DeptServiceImpl::class.java to "createDept",
            DeptServiceImpl::class.java to "updateDept",
            DeptServiceImpl::class.java to "deleteDept",
            DeptServiceImpl::class.java to "deleteDeptList",
            OAuth2ClientServiceImpl::class.java to "updateOAuth2Client",
            OAuth2ClientServiceImpl::class.java to "deleteOAuth2Client",
            OAuth2ClientServiceImpl::class.java to "deleteOAuth2ClientList",
            MailAccountServiceImpl::class.java to "updateMailAccount",
            MailAccountServiceImpl::class.java to "deleteMailAccount",
            MailAccountServiceImpl::class.java to "deleteMailAccountList",
            MailTemplateServiceImpl::class.java to "updateMailTemplate",
            MailTemplateServiceImpl::class.java to "deleteMailTemplate",
            MailTemplateServiceImpl::class.java to "deleteMailTemplateList",
            NotifyTemplateServiceImpl::class.java to "updateNotifyTemplate",
            NotifyTemplateServiceImpl::class.java to "deleteNotifyTemplate",
            NotifyTemplateServiceImpl::class.java to "deleteNotifyTemplateList",
            SmsTemplateServiceImpl::class.java to "updateSmsTemplate",
            SmsTemplateServiceImpl::class.java to "deleteSmsTemplate",
            SmsTemplateServiceImpl::class.java to "deleteSmsTemplateList",
        )
        methods.forEach { (type, name) -> assertAnnotated(type, name, CacheEvict::class.java) }
    }

    private fun assertAnnotated(type: Class<*>, methodName: String, annotation: Class<out Annotation>) {
        val method = type.methods.first { it.name == methodName }
        assertTrue(
            method.declaredAnnotations.any(annotation::isInstance),
            "${type.simpleName}.$methodName must retain @${annotation.simpleName}",
        )
    }
}
