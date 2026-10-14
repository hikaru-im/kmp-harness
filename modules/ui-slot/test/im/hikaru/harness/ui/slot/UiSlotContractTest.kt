package im.hikaru.harness.ui.slot

import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.plugin.simplePlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class UiSlotContractTest {
    private val single = SingleUiSlotKey<String>("single")
    private val keyed = KeyedUiSlotKey<String, String>("keyed")
    private val list = ListUiSlotKey<String>("list")
    private val schema = UiSchema(listOf(single, keyed, list))

    @Test
    fun schemaShouldRejectDuplicateNamesEvenForDifferentKeyObjects() {
        assertFailsWith<IllegalArgumentException> {
            UiSchema(
                listOf(
                    SingleUiSlotKey<String>("same"),
                    ListUiSlotKey<String>("same"),
                )
            )
        }
    }

    @Test
    fun singleShouldChooseLowestOrderAndRejectSameOrder() {
        val builder = UiContributionBuilder(schema)
        builder.contribute(single, order = 20) { }
        builder.contribute(single, order = -10) { }

        assertEquals(-10, assertNotNull(builder.freeze().single(single)).order)

        val conflicting = UiContributionBuilder(schema)
        conflicting.contribute(single, order = 0) { }
        conflicting.contribute(single, order = 0) { }
        assertFailsWith<IllegalStateException> { conflicting.freeze() }
    }

    @Test
    fun keyedShouldKeepCellsIndependentAndRejectCellConflict() {
        val builder = UiContributionBuilder(schema)
        builder.contribute(keyed, cell = "bash", order = 10) { _, _ -> }
        builder.contribute(keyed, cell = "edit", order = 0) { _, _ -> }

        val snapshot = builder.freeze()
        assertEquals(10, assertNotNull(snapshot.keyed(keyed, "bash")).order)
        assertEquals(0, assertNotNull(snapshot.keyed(keyed, "edit")).order)
        assertEquals(null, snapshot.keyed(keyed, "unknown"))

        val conflicting = UiContributionBuilder(schema)
        conflicting.contribute(keyed, cell = "bash", order = 0) { _, _ -> }
        conflicting.contribute(keyed, cell = "bash", order = 0) { _, _ -> }
        assertFailsWith<IllegalStateException> { conflicting.freeze() }
    }

    @Test
    fun listShouldSortByOrderThenCatalogSequenceAndRejectDuplicateIds() {
        val builder = UiContributionBuilder(schema)
        builder.contribute(list, id = "late", order = 20) { }
        builder.contribute(list, id = "first", order = 0) { }
        builder.contribute(list, id = "second", order = 0) { }

        assertEquals(listOf("first", "second", "late"), builder.freeze().list(list).map { it.id })

        val duplicate = UiContributionBuilder(schema)
        duplicate.contribute(list, id = "same") { }
        duplicate.contribute(list, id = "same") { }
        assertFailsWith<IllegalStateException> { duplicate.freeze() }
    }

    @Test
    fun freezeShouldRejectWritesAndSnapshotShouldStayStable() {
        val builder = UiContributionBuilder(schema)
        builder.contribute(list, id = "one") { }
        val snapshot = builder.freeze()

        assertFailsWith<IllegalStateException> {
            builder.contribute(list, id = "two") { }
        }
        assertEquals(listOf("one"), snapshot.list(list).map { it.id })
    }

    @Test
    fun clientHostShouldInstallCompiledPluginsAndFreezeSnapshot() =
        kotlinx.coroutines.test.runTest {
            val first = SingleUiSlotKey<String>("first")
            val clientSchema = UiSchema(listOf(first))
            val plugin =
                simplePlugin {
                    inject(UiContributionKey)
                    apply { context, _ ->
                        context.require(UiContributionKey).contribute(first) { }
                    }
                }
            val host =
                ClientHarnessHost.start(
                    catalog = PluginCatalog(listOf(pluginDefinition("first-plugin", plugin))),
                    entries = listOf(Entry("first", "first-plugin")),
                    schema = clientSchema,
                )

            assertEquals(0, host.snapshot.single(first)?.order)
            host.close()
        }
}
