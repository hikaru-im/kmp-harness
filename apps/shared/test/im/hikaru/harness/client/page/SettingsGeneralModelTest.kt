package im.hikaru.harness.client.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsGeneralModelTest {
    @Test
    fun generalModelKeepsPermissionAndPreferenceContractsStable() {
        assertEquals(listOf("default-permission", "full-disk-access"), SettingsGeneralModel.permissions.map { it.id })
        assertEquals(13, SettingsGeneralModel.preferences.size)
        assertEquals("task-folder", SettingsGeneralModel.preferences.first().id)
        assertEquals("plugins", SettingsGeneralModel.preferences.last().id)
        assertEquals(
            listOf("task-folder", "default-file-app", "language", "confirm-close", "terminal-location", "licenses"),
            SettingsGeneralModel.preferences.filter { it.control == SettingsGeneralControl.Dropdown || it.control == SettingsGeneralControl.Segmented || it.control == SettingsGeneralControl.Button }.map { it.id },
        )
    }

    @Test
    fun toggleDefaultsMatchTheReferenceState() {
        val rows = (SettingsGeneralModel.permissions + SettingsGeneralModel.preferences)
        val defaults = rows.filter { it.control == SettingsGeneralControl.Toggle }.associate { it.id to it.initiallyChecked }
        assertEquals(true, defaults["default-permission"])
        assertEquals(true, defaults["full-disk-access"])
        assertEquals(true, defaults["menu-bar"])
        assertEquals(false, defaults["compress-history"])
        assertEquals(true, defaults["plugins"])
        assertTrue(defaults.values.all { it != null })
        assertFalse(defaults["compress-history"] == true)
    }

}
