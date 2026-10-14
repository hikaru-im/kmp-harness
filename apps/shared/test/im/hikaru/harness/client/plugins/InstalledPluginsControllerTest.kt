package im.hikaru.harness.client.plugins

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InstalledPluginsControllerTest {
    @Test
    fun defaultRepositoryIsUnavailableAndCannotRefreshOrSearch() = runTest {
        val controller = InstalledPluginsController(this)
        controller.bind(scope())
        assertEquals(
            PluginLoadState.Unavailable(PluginUnavailableReason.PluginServiceNotExposed),
            controller.state.value.list,
        )
        assertFalse(controller.state.value.canRefresh)
        assertFalse(controller.state.value.canSearch)
        assertNull(controller.state.value.visiblePlugins)
        controller.dispatch(InstalledPluginsAction.Search("fiction"))
        controller.dispatch(InstalledPluginsAction.SetEnabledOnly(true))
        controller.refresh()
        assertEquals("", controller.state.value.searchQuery)
        assertFalse(controller.state.value.enabledOnly)
        controller.select(PluginId("unavailable-detail"))
        assertEquals(
            PluginLoadState.Unavailable(PluginUnavailableReason.PluginServiceNotExposed),
            controller.state.value.detail,
        )
        assertEquals(
            PluginResult.Unavailable(PluginUnavailableReason.PluginServiceNotExposed),
            UnavailablePluginRepository.listInstalled(scope()),
        )
        controller.close()
    }

    @Test
    fun loadingIsDistinctFromRealEmptyAndSearchRequiresSuccessfulData() = runTest {
        val release = CompletableDeferred<PluginResult<List<InstalledPlugin>>>()
        val repository = TestRepository(list = { release.await() })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        assertIs<PluginLoadState.Loading>(controller.state.value.list)
        assertNull(controller.state.value.visiblePlugins)
        controller.updateSearch("ignored while loading")
        assertEquals("", controller.state.value.searchQuery)
        runCurrent()
        release.complete(PluginResult.Success(emptyList()))
        runCurrent()
        assertEquals(PluginLoadState.Success(emptyList<InstalledPlugin>()), controller.state.value.list)
        assertEquals(emptyList<InstalledPlugin>(), controller.state.value.visiblePlugins)
        assertTrue(controller.state.value.canSearch)
        assertTrue(controller.state.value.canRefresh)
        controller.close()
    }

    @Test
    fun searchAndEnabledFilterShareRealDataAndNeverAssumeUnknownIsEnabled() = runTest {
        val enabled = plugin("alpha", "Alpha tools", PluginEnabledState.Enabled)
        val disabled = plugin("beta", "Beta tools", PluginEnabledState.Disabled)
        val unknown = plugin("gamma", "Gamma tools")
        val repository = TestRepository(list = { PluginResult.Success(listOf(enabled, disabled, unknown)) })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        runCurrent()
        controller.dispatch(InstalledPluginsAction.Search("TOOLS"))
        assertEquals(listOf(enabled, disabled, unknown), controller.state.value.visiblePlugins)
        controller.dispatch(InstalledPluginsAction.SetEnabledOnly(true))
        assertEquals(listOf(enabled), controller.state.value.visiblePlugins)
        controller.updateSearch("beta")
        assertEquals(emptyList<InstalledPlugin>(), controller.state.value.visiblePlugins)
        controller.dispatch(InstalledPluginsAction.SetEnabledOnly(false))
        assertEquals(listOf(disabled), controller.state.value.visiblePlugins)
        controller.close()
    }

    @Test
    fun scopeGenerationAndAccountFenceNonCooperativeOldListReplies() = runTest {
        val oldScope = scope(generation = 1, account = "tenant-a/member")
        val newScope = scope(generation = 2, account = "tenant-b/member")
        val oldRelease = CompletableDeferred<PluginResult<List<InstalledPlugin>>>()
        val fresh = plugin("fresh", "New account plugin")
        val repository = TestRepository(list = {
            if (it == oldScope) withContext(NonCancellable) { oldRelease.await() }
            else PluginResult.Success(listOf(fresh))
        })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(oldScope)
        runCurrent()
        controller.bind(newScope)
        runCurrent()
        oldRelease.complete(PluginResult.Success(listOf(plugin("private", "Old account private plugin"))))
        runCurrent()
        assertEquals(newScope, controller.state.value.scope)
        assertEquals(listOf(fresh), controller.state.value.visiblePlugins)
        controller.close()
    }

    @Test
    fun latestListRequestWinsEvenWhenOldAdapterIgnoresCancellation() = runTest {
        val oldRelease = CompletableDeferred<PluginResult<List<InstalledPlugin>>>()
        var reads = 0
        val fresh = plugin("fresh", "Latest")
        val repository = TestRepository(list = {
            if (++reads == 1) withContext(NonCancellable) { oldRelease.await() }
            else PluginResult.Success(listOf(fresh))
        })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        runCurrent()
        controller.load()
        runCurrent()
        oldRelease.complete(PluginResult.Error(PluginRepositoryError("Old request error")))
        runCurrent()
        assertEquals(listOf(fresh), controller.state.value.visiblePlugins)
        controller.close()
    }

    @Test
    fun accountChangeWithoutGenerationChangeStillClearsOldDataAndFilters() = runTest {
        val repository = TestRepository(list = {
            val account = requireNotNull(it.accountId).value
            PluginResult.Success(listOf(plugin(account, account, PluginEnabledState.Enabled)))
        })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope(account = "tenant-a/member"))
        runCurrent()
        controller.updateSearch("tenant-a")
        controller.setEnabledOnly(true)
        controller.bind(scope(account = "tenant-b/member"))
        assertEquals("", controller.state.value.searchQuery)
        assertFalse(controller.state.value.enabledOnly)
        assertNull(controller.state.value.visiblePlugins)
        runCurrent()
        assertEquals(listOf(PluginId("tenant-b/member")), controller.state.value.visiblePlugins?.map { it.id })
        controller.close()
    }

    @Test
    fun disconnectClearsSelectionAndFiltersAndRejectsLateDetails() = runTest {
        val release = CompletableDeferred<PluginResult<InstalledPluginDetail>>()
        val installed = plugin("one", "One", PluginEnabledState.Enabled)
        val repository = TestRepository(
            list = { PluginResult.Success(listOf(installed)) },
            detail = { _, _ -> withContext(NonCancellable) { release.await() } },
        )
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        runCurrent()
        controller.updateSearch("one")
        controller.setEnabledOnly(true)
        controller.select(installed.id)
        runCurrent()
        controller.bind(null)
        assertEquals(InstalledPluginsState(), controller.state.value)
        release.complete(PluginResult.Success(InstalledPluginDetail(installed)))
        runCurrent()
        assertEquals(InstalledPluginsState(), controller.state.value)
        controller.close()
    }

    @Test
    fun backAndNewSelectionFenceLateDetailReplies() = runTest {
        val release = CompletableDeferred<PluginResult<InstalledPluginDetail>>()
        val old = plugin("old", "Old")
        val fresh = plugin("fresh", "Fresh")
        val repository = TestRepository(detail = { _, id ->
            if (id == old.id) withContext(NonCancellable) { release.await() }
            else PluginResult.Success(InstalledPluginDetail(fresh))
        })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        runCurrent()
        controller.select(old.id)
        runCurrent()
        controller.back()
        assertNull(controller.state.value.selectedId)
        assertIs<PluginLoadState.NotLoaded>(controller.state.value.detail)
        controller.select(fresh.id)
        runCurrent()
        release.complete(PluginResult.Success(InstalledPluginDetail(old)))
        runCurrent()
        assertEquals(fresh.id, controller.state.value.selectedId)
        assertEquals(PluginLoadState.Success(InstalledPluginDetail(fresh)), controller.state.value.detail)
        controller.close()
    }

    @Test
    fun serviceErrorsRetryButAuthUnsupportedAndUnavailableRemainExplicit() = runTest {
        var response: PluginResult<List<InstalledPlugin>> = PluginResult.Error(PluginRepositoryError("Read denied"))
        val repository = TestRepository(list = { response })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        runCurrent()
        assertIs<PluginLoadState.Error>(controller.state.value.list)
        assertTrue(controller.state.value.canRefresh)
        response = PluginResult.AuthRequired("Authenticate on this host")
        controller.refresh()
        runCurrent()
        assertEquals(PluginLoadState.AuthRequired("Authenticate on this host"), controller.state.value.list)
        assertNull(controller.state.value.visiblePlugins)
        response = PluginResult.UnsupportedHost("This host exposes no user-plugin service")
        controller.refresh()
        runCurrent()
        assertIs<PluginLoadState.UnsupportedHost>(controller.state.value.list)
        assertFalse(controller.state.value.canRefresh)
        controller.bind(scope(generation = 2))
        response = PluginResult.Unavailable(PluginUnavailableReason.PluginServiceNotExposed)
        runCurrent()
        assertIs<PluginLoadState.Unavailable>(controller.state.value.list)
        assertFalse(controller.state.value.canRefresh)
        controller.close()
    }

    @Test
    fun detailIdMismatchBecomesErrorAndRetryUsesActualAdapter() = runTest {
        val expected = plugin("requested", "Requested")
        var reads = 0
        val repository = TestRepository(detail = { _, _ ->
            PluginResult.Success(InstalledPluginDetail(if (++reads == 1) plugin("wrong", "Wrong") else expected))
        })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        controller.select(expected.id)
        runCurrent()
        assertIs<PluginLoadState.Error>(controller.state.value.detail)
        assertTrue(controller.state.value.canRetryDetail)
        controller.dispatch(InstalledPluginsAction.RetryDetail)
        runCurrent()
        assertEquals(PluginLoadState.Success(InstalledPluginDetail(expected)), controller.state.value.detail)
        assertEquals(PluginAppsState.NotLoaded, (controller.state.value.detail as PluginLoadState.Success).value.apps)
        controller.close()
    }

    @Test
    fun thrownReadFailureIsAnErrorAndBindingSameScopeDoesNotDuplicateReads() = runTest {
        var reads = 0
        val repository = TestRepository(list = { ++reads; error("Transport failed") })
        val controller = InstalledPluginsController(this, repository)
        controller.bind(scope())
        controller.bind(scope())
        runCurrent()
        assertEquals(1, reads)
        assertEquals(PluginLoadState.Error(PluginRepositoryError("Transport failed")), controller.state.value.list)
        controller.close()
        controller.bind(scope(generation = 3))
        assertEquals(InstalledPluginsState(), controller.state.value)
        assertEquals(1, reads)
    }
}

private fun scope(generation: Long = 1, account: String = "tenant-a/member") =
    PluginScope(HostId("plugin-test-host"), generation, PluginAccountId(account))

private fun plugin(id: String, name: String, enabled: PluginEnabledState = PluginEnabledState.Unknown) =
    InstalledPlugin(PluginId(id), name, enabled = enabled)

/** These responses are test fixtures only and cannot be selected by production defaults. */
private class TestRepository(
    private val list: suspend (PluginScope) -> PluginResult<List<InstalledPlugin>> = { PluginResult.Success(emptyList()) },
    private val detail: suspend (PluginScope, PluginId) -> PluginResult<InstalledPluginDetail> = { _, id ->
        PluginResult.Success(InstalledPluginDetail(InstalledPlugin(id, id.value)))
    },
) : InstalledPluginRepository {
    override val canRead: Boolean = true
    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> = list(scope)
    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> = detail(scope, id)
}
