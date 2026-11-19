package im.hikaru.harness.client.app

import im.hikaru.harness.client.settings.*
import kotlin.test.*

class ModelPickerChoicesTest {
    @Test
    fun enabledModelsAreFlatAndDuplicateNamesKeepConnectionIdentity() {
        val snapshot = ModelSettingsSnapshot(providers = listOf(
            ConfigurableProvider("a", "Personal", active = true, disabledModelIds = setOf("off"),
                models = listOf(ProviderModel("one", "Same"), ProviderModel("off", "Disabled"), ProviderModel("image", "Image", selectable = false))),
            ConfigurableProvider("b", "Team", active = true, models = listOf(ProviderModel("two", "Same"))),
            ConfigurableProvider("c", "Inactive", models = listOf(ProviderModel("hidden", "Hidden"))),
        ))
        val choices = snapshot.composerModelChoices()
        assertEquals(listOf("one", "two"), choices.map { it.modelId })
        assertEquals(listOf("Same · Personal", "Same · Team"), choices.map { it.label })
        assertEquals(listOf("a", "b"), choices.map { it.providerId })
    }
}
