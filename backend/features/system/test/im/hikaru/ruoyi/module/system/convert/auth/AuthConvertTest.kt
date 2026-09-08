package im.hikaru.ruoyi.module.system.convert.auth

import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO
import im.hikaru.ruoyi.module.system.enums.permission.MenuTypeEnum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthConvertTest {

    @Test
    fun `menu tree only initializes children for parent nodes`() {
        val directory = menu(1L, MenuDO.ID_ROOT, MenuTypeEnum.DIR, 2, "System", "/system")
        val userMenu = menu(100L, 1L, MenuTypeEnum.MENU, 2, "User", "user").apply {
            component = "system/user/index"
            componentName = "SystemUser"
        }
        val roleMenu = menu(101L, 1L, MenuTypeEnum.MENU, 1, "Role", "role")
        val button = menu(1001L, 100L, MenuTypeEnum.BUTTON, 1, "Query", "")

        val roots = AuthConvert.buildMenuTree(listOf(directory, userMenu, roleMenu, button))

        assertEquals(1, roots.size)
        val root = roots.single()
        assertEquals(listOf(101L, 100L), root.children?.map { it.id })
        root.children.orEmpty().forEach { assertNull(it.children) }
    }

    private fun menu(
        id: Long,
        parentId: Long,
        type: MenuTypeEnum,
        sort: Int,
        name: String,
        path: String,
    ) = MenuDO().apply {
        this.id = id
        this.parentId = parentId
        this.type = type.type
        this.sort = sort
        this.name = name
        this.path = path
        visible = true
        keepAlive = true
        alwaysShow = true
    }
}
