package im.hikaru.ruoyi.module.system.controller.admin

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.system.controller.admin.auth.AuthController
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO
import im.hikaru.ruoyi.module.system.controller.admin.dept.PostController
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post.PostPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.NotifyTemplateController
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.permission.RoleController
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.RolePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.SmsChannelController
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.tenant.TenantController
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantPageReqVO
import im.hikaru.ruoyi.module.system.controller.app.tenant.AppTenantController
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.PostDO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsChannelDO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantDO
import im.hikaru.ruoyi.module.system.service.dept.PostService
import im.hikaru.ruoyi.module.system.service.notify.NotifySendService
import im.hikaru.ruoyi.module.system.service.notify.NotifyTemplateService
import im.hikaru.ruoyi.module.system.service.permission.RoleService
import im.hikaru.ruoyi.module.system.service.sms.SmsChannelService
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import jakarta.annotation.security.PermitAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletResponse
import java.lang.reflect.ParameterizedType

class SystemAdminEndpointParityTest {

    @Test
    fun `permission info endpoint keeps the reference response contract`() {
        val endpoint = AuthController::class.java.getDeclaredMethod("getPermissionInfo")
        val responseType = endpoint.genericReturnType as ParameterizedType

        assertEquals(AuthPermissionInfoRespVO::class.java, responseType.actualTypeArguments.single())
    }

    @Test
    fun `post role and tenant exports produce excel responses`() {
        val postService = mock(PostService::class.java)
        val postReq = PostPageReqVO()
        `when`(postService.getPostPage(postReq)).thenReturn(PageResult(1, listOf(PostDO().apply {
            id = 1
            name = "Developer"
            code = "developer"
        })))
        assertExcelResponse(MockHttpServletResponse().also { PostController(postService).export(it, postReq) })
        assertEquals(PageParam.PAGE_SIZE_NONE, postReq.pageSize)

        val roleService = mock(RoleService::class.java)
        val roleReq = RolePageReqVO()
        `when`(roleService.getRolePage(roleReq)).thenReturn(PageResult(1, listOf(RoleDO().apply {
            id = 2
            name = "Operator"
            code = "operator"
        })))
        assertExcelResponse(MockHttpServletResponse().also { RoleController(roleService).export(it, roleReq) })
        assertEquals(PageParam.PAGE_SIZE_NONE, roleReq.pageSize)

        val tenantService = mock(TenantService::class.java)
        val tenantReq = TenantPageReqVO()
        `when`(tenantService.getTenantPage(tenantReq)).thenReturn(PageResult(1, listOf(TenantDO().apply {
            id = 3
            name = "Acme"
        })))
        assertExcelResponse(MockHttpServletResponse().also { TenantController(tenantService).export(tenantReq, it) })
        assertEquals(PageParam.PAGE_SIZE_NONE, tenantReq.pageSize)
    }

    @Test
    fun `notify template and sms channel exports produce excel responses`() {
        val notifyService = mock(NotifyTemplateService::class.java)
        val notifyReq = NotifyTemplatePageReqVO()
        `when`(notifyService.getNotifyTemplatePage(notifyReq)).thenReturn(PageResult(1, listOf(NotifyTemplateDO().apply {
            id = 4
            name = "System notice"
            code = "system-notice"
            content = "Hello"
        })))
        val notifyController = NotifyTemplateController(notifyService, mock(NotifySendService::class.java))
        assertExcelResponse(MockHttpServletResponse().also { notifyController.export(it, notifyReq) })
        assertEquals(PageParam.PAGE_SIZE_NONE, notifyReq.pageSize)

        val smsService = mock(SmsChannelService::class.java)
        val smsReq = SmsChannelPageReqVO()
        `when`(smsService.getSmsChannelPage(smsReq)).thenReturn(PageResult(1, listOf(SmsChannelDO().apply {
            id = 5
            signature = "Example"
            code = "ALIYUN"
        })))
        assertExcelResponse(MockHttpServletResponse().also { SmsChannelController(smsService).export(it, smsReq) })
        assertEquals(PageParam.PAGE_SIZE_NONE, smsReq.pageSize)
    }

    @Test
    fun `public tenant discovery matches reference routes and ignores tenant filtering`() {
        val service = mock(TenantService::class.java)
        val tenant = TenantDO().apply {
            id = 42
            name = "Acme"
            status = CommonStatusEnum.ENABLE.status
        }
        `when`(service.getTenantByName("Acme")).thenReturn(tenant)
        `when`(service.getTenantByWebsite("acme.example.com")).thenReturn(tenant)
        `when`(service.getTenantListByStatus(CommonStatusEnum.ENABLE.status)).thenReturn(listOf(tenant))

        val controller = TenantController(service)
        assertEquals(42L, controller.getIdByName("Acme").data)
        assertEquals(listOf("Acme"), controller.simpleList().data?.map { it.name })
        assertEquals("Acme", controller.getByWebsite("acme.example.com").data?.name)

        assertPublicTenantEndpoint(TenantController::class.java, "getIdByName", String::class.java)
        assertPublicTenantEndpoint(TenantController::class.java, "simpleList")
        assertPublicTenantEndpoint(TenantController::class.java, "getByWebsite", String::class.java)
        assertPublicTenantEndpoint(AppTenantController::class.java, "getByWebsite", String::class.java)
    }

    private fun assertExcelResponse(response: MockHttpServletResponse) {
        assertTrue(response.contentAsByteArray.size > 100)
        assertTrue(response.getHeader("Content-Disposition")?.startsWith("attachment;filename=") == true)
        assertTrue(response.contentType?.startsWith("application/vnd.ms-excel") == true)
    }

    private fun assertPublicTenantEndpoint(type: Class<*>, method: String, vararg parameters: Class<*>) {
        val endpoint = type.getDeclaredMethod(method, *parameters)
        assertTrue(endpoint.isAnnotationPresent(PermitAll::class.java))
        assertTrue(endpoint.isAnnotationPresent(TenantIgnore::class.java))
    }
}
