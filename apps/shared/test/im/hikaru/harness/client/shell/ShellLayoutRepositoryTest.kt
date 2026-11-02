package im.hikaru.harness.client.shell

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ShellLayoutRepositoryTest {
    @Test
    fun loadRestoresEveryPersistedFieldAndClampsTheWidth() = runBlocking {
        val store = MemoryShellLayoutStore(
            ShellLayoutSettings(
                navigatorMode = SidebarMode.Collapsed,
                navigatorWidth = 900f,
                pinnedRailDestinations = setOf(RailDestination.Home, RailDestination.Plugins),
                summaryExpandedSections = mapOf("plan" to false),
            ),
        )
        val repository = ShellLayoutRepository(store)

        repository.load()

        val state = repository.state.value
        assertTrue(state.loaded)
        assertNull(state.loadError)
        assertEquals(SidebarMode.Collapsed, state.settings.navigatorMode)
        assertEquals(NavigatorMaxWidth.value, state.settings.navigatorWidth)
        assertEquals(setOf(RailDestination.Home, RailDestination.Plugins), state.settings.pinnedRailDestinations)
        assertEquals(mapOf("plan" to false), state.settings.summaryExpandedSections)
    }

    @Test
    fun widthIsClampedIntoTheNavigatorRangeBeforeItIsUsed() = runBlocking {
        listOf(
            Float.NaN to NavigatorPreferredWidth.value,
            Float.POSITIVE_INFINITY to NavigatorPreferredWidth.value,
            -40f to NavigatorMinWidth.value,
            12_000f to NavigatorMaxWidth.value,
        ).forEach { (stored, expected) ->
            val repository = ShellLayoutRepository(MemoryShellLayoutStore(ShellLayoutSettings(navigatorWidth = stored)))
            repository.load()
            assertEquals(expected, repository.state.value.settings.navigatorWidth, "stored=$stored")
        }
    }

    @Test
    fun temporarilyExpandedIsReDerivedFromTheWindowInsteadOfRestored() = runBlocking {
        val repository = ShellLayoutRepository(
            MemoryShellLayoutStore(ShellLayoutSettings(navigatorMode = SidebarMode.TemporarilyExpanded)),
        )

        repository.load()

        assertEquals(SidebarMode.Expanded, repository.state.value.settings.navigatorMode)
    }

    @Test
    fun damagedDocumentFallsBackToDefaultsAndTheNextWriteReplacesIt() = runBlocking {
        var stored: ShellLayoutSettings? = null
        val store = object : ShellLayoutStore {
            override val durable = true
            override suspend fun read(): ShellLayoutSettings? = error("bad json")
            override suspend fun write(settings: ShellLayoutSettings) { stored = settings }
        }
        val repository = ShellLayoutRepository(store)

        repository.load()

        assertTrue(repository.state.value.loaded)
        assertNotNull(repository.state.value.loadError)
        assertEquals(ShellLayoutSettings(), repository.state.value.settings)
        assertTrue(repository.save(ShellLayoutSettings(navigatorMode = SidebarMode.Collapsed)))
        assertEquals(SidebarMode.Collapsed, stored?.navigatorMode)
        assertEquals(SidebarMode.Collapsed, repository.state.value.settings.navigatorMode)
    }

    @Test
    fun failedWriteKeepsTheLastGoodLayoutAndReportsTheError() = runBlocking {
        var fail = false
        val store = object : ShellLayoutStore {
            override val durable = true
            override suspend fun read() = ShellLayoutSettings(navigatorWidth = 300f)
            override suspend fun write(settings: ShellLayoutSettings) {
                if (fail) error("disk full")
            }
        }
        val repository = ShellLayoutRepository(store)
        repository.load()

        fail = true
        assertFalse(repository.save(ShellLayoutSettings(navigatorWidth = 400f)))

        assertEquals(300f, repository.state.value.settings.navigatorWidth)
        assertNotNull(repository.state.value.saveError)
        assertEquals(0, repository.state.value.saveRevision)
    }

    @Test
    fun unchangedLayoutIsNotWrittenAgainAndSavingBeforeLoadIsRejected() = runBlocking {
        var writes = 0
        val settings = ShellLayoutSettings(navigatorWidth = 320f)
        val store = object : ShellLayoutStore {
            override val durable = true
            override suspend fun read() = settings
            override suspend fun write(settings: ShellLayoutSettings) { writes++ }
        }
        val repository = ShellLayoutRepository(store)

        assertFalse(repository.save(settings))
        repository.load()
        assertTrue(repository.save(settings))

        assertEquals(0, writes)
        assertEquals(0, repository.state.value.saveRevision)
    }
}
