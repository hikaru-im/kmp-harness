package im.hikaru.harness.client.plugins

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PluginManagementControllerTest {
    @Test
    fun registrationEnableDisableAndRemoveRefreshAuthoritativeData() = runTest {
        val repository = ManagementFixtureRepository()
        val controller = InstalledPluginsController(this, repository)
        controller.bind(managementScope())
        runCurrent()
        assertEquals(listOf("list", "revision"), repository.reads)
        assertTrue(controller.state.value.canManage)

        controller.dispatch(InstalledPluginsAction.BeginRegistration)
        controller.dispatch(InstalledPluginsAction.ChangeRegistrationDirectory("relative/path"))
        controller.dispatch(InstalledPluginsAction.RegisterLocal)
        assertTrue(repository.writes.isEmpty())
        controller.dispatch(InstalledPluginsAction.ChangeRegistrationDirectory(" /source/plugin "))
        controller.dispatch(InstalledPluginsAction.RegisterLocal)
        assertTrue(controller.state.value.mutationBusy)
        assertEquals(" /source/plugin ", controller.state.value.registrationDirectory)
        runCurrent()
        assertNull(controller.state.value.registrationDirectory)
        assertEquals("/source/plugin", repository.plugins.single().source)
        assertEquals(PluginEnabledState.Disabled, controller.state.value.visiblePlugins!!.single().enabled)
        assertEquals(PluginMutationReceipt("1"), controller.state.value.mutationReceipt)
        assertEquals(PluginLoadState.Success("1"), controller.state.value.revision)

        controller.select(PluginId("fixture"))
        runCurrent()
        val detail = assertIs<PluginLoadState.Success<InstalledPluginDetail>>(controller.state.value.detail).value
        assertEquals(PluginSkillsState.Available(listOf(PluginSkillSummary("Review", "Review source text"))), detail.skills)
        assertEquals(listOf("Commands 尚未支持。"), detail.unsupportedAssets)
        assertIs<PluginAppsState.Unavailable>(detail.apps)
        controller.dispatch(InstalledPluginsAction.SetEnabled(detail.plugin.id, true))
        runCurrent()
        assertEquals(PluginEnabledState.Enabled,
            assertIs<PluginLoadState.Success<InstalledPluginDetail>>(controller.state.value.detail).value.plugin.enabled)
        assertTrue(controller.state.value.managementNotice.orEmpty().contains("下一次 Agent 请求"))
        controller.dispatch(InstalledPluginsAction.SetEnabled(detail.plugin.id, false))
        runCurrent()
        assertEquals(PluginEnabledState.Disabled, controller.state.value.visiblePlugins!!.single().enabled)

        controller.dispatch(InstalledPluginsAction.RequestRemove(detail.plugin.id))
        assertEquals(detail.plugin.id, controller.state.value.removeConfirmation?.id)
        controller.dispatch(InstalledPluginsAction.RemoveRegistration)
        runCurrent()
        assertNull(controller.state.value.selectedId)
        assertNull(controller.state.value.removeConfirmation)
        assertEquals(emptyList(), controller.state.value.visiblePlugins)
        assertEquals(PluginMutationReceipt("4"), controller.state.value.mutationReceipt)
        assertTrue(controller.state.value.managementNotice.orEmpty().contains("源文件均已保留"))
        assertEquals(listOf("0", "1", "2", "3"), repository.writes.map { it.revision })
        assertEquals(listOf("register", "enable", "disable", "remove"), repository.writes.map { it.operation })
        controller.close()
    }

    @Test
    fun casFailureKeepsActualDisabledStateAndRequiresExplicitRefreshWithoutRetry() = runTest {
        val repository = ManagementFixtureRepository(seed = true)
        val controller = InstalledPluginsController(this, repository)
        controller.bind(managementScope())
        runCurrent()
        controller.select(PluginId("fixture"))
        runCurrent()
        repository.revision = "external-change"
        val readsBeforeWrite = repository.reads.size
        controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true))
        runCurrent()
        assertEquals(readsBeforeWrite, repository.reads.size)
        assertNull(controller.state.value.mutationReceipt)
        assertEquals(PluginEnabledState.Disabled,
            assertIs<PluginLoadState.Success<InstalledPluginDetail>>(controller.state.value.detail).value.plugin.enabled)
        assertTrue(controller.state.value.mutationRequiresRefresh)
        assertTrue(controller.state.value.mutationError.orEmpty().contains("CAS"))
        assertFalse(controller.state.value.canManage)
        controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true))
        controller.load() // A background load is not user authorization to retry a write.
        runCurrent()
        assertEquals(1, repository.writes.size)
        assertFalse(controller.state.value.canManage)
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.canManageDetail)
        assertNull(controller.state.value.mutationError)
        assertEquals(1, repository.writes.size)
        controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true))
        runCurrent()
        assertEquals(2, repository.writes.size)
        assertEquals("external-change", repository.writes.last().revision)
        controller.close()
    }

    @Test
    fun scopeReplacementDuringWriteBlocksAllNewWritesAndRejectsOldReceipt() = runTest {
        val release = CompletableDeferred<Unit>()
        val repository = ManagementFixtureRepository(seed = true).apply {
            beforeWrite = { withContext(NonCancellable) { release.await() } }
        }
        val controller = InstalledPluginsController(this, repository)
        val oldScope = managementScope()
        val freshScope = managementScope(2)
        repository.acceptedScope = oldScope
        controller.bind(oldScope)
        runCurrent()
        controller.select(PluginId("fixture"))
        runCurrent()
        controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true))
        runCurrent()
        repository.acceptedScope = freshScope // Models the parent's bindAccess adapter commit guard.
        controller.bind(freshScope)
        runCurrent()
        controller.dispatch(InstalledPluginsAction.BeginRegistration)
        assertNull(controller.state.value.registrationDirectory)
        assertTrue(controller.state.value.mutationBusy)
        controller.select(PluginId("fixture"))
        runCurrent()
        controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true))
        assertEquals(1, repository.writes.size)
        release.complete(Unit)
        runCurrent()
        assertEquals(freshScope, controller.state.value.scope)
        assertFalse(controller.state.value.mutationBusy)
        assertNull(controller.state.value.mutationReceipt)
        assertNull(controller.state.value.managementNotice)
        assertNull(controller.state.value.mutationError)
        assertEquals(PluginEnabledState.Disabled, repository.plugins.single().enabled)
        assertEquals(oldScope, repository.writes.single().scope)
        controller.close()
    }

    @Test
    fun duplicateWritesAreSingleFlightAndRefreshNeverCancelsTheWrite() = runTest {
        val release = CompletableDeferred<Unit>()
        val repository = ManagementFixtureRepository(seed = true).apply { beforeWrite = { release.await() } }
        val controller = InstalledPluginsController(this, repository)
        controller.bind(managementScope())
        runCurrent()
        controller.select(PluginId("fixture"))
        runCurrent()
        repeat(2) { controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true)) }
        controller.refresh()
        runCurrent()
        controller.dispatch(InstalledPluginsAction.RequestRemove(PluginId("fixture")))
        controller.dispatch(InstalledPluginsAction.BeginRegistration)
        assertEquals(1, repository.writes.size)
        assertTrue(controller.state.value.mutationBusy)
        assertNull(controller.state.value.removeConfirmation)
        assertNull(controller.state.value.registrationDirectory)
        release.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.mutationBusy)
        assertEquals(PluginMutationReceipt("1"), controller.state.value.mutationReceipt)
        assertEquals(PluginEnabledState.Enabled, controller.state.value.visiblePlugins!!.single().enabled)
        assertTrue(controller.state.value.canManageDetail)
        controller.close()
    }

    @Test
    fun failedPostWriteListAndDetailRefreshPreserveReceiptAndNotice() = runTest {
        val repository = ManagementFixtureRepository(seed = true)
        val controller = InstalledPluginsController(this, repository)
        controller.bind(managementScope())
        runCurrent()
        controller.select(PluginId("fixture"))
        runCurrent()
        repository.afterWrite = { repository.failList = true; repository.failDetail = true }
        controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true))
        runCurrent()
        assertEquals(PluginMutationReceipt("1"), controller.state.value.mutationReceipt)
        assertTrue(controller.state.value.managementNotice.orEmpty().contains("已启用"))
        assertIs<PluginLoadState.Error>(controller.state.value.list)
        assertIs<PluginLoadState.Error>(controller.state.value.detail)
        assertNull(controller.state.value.visiblePlugins)
        assertFalse(controller.state.value.canManage)
        repository.failList = false
        repository.failDetail = false
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.canManageDetail)
        assertEquals(1, repository.writes.size)
        assertEquals(PluginMutationReceipt("1"), controller.state.value.mutationReceipt)
        controller.close()
    }

    @Test
    fun registrationClosesOnReceiptEvenWhenItsRefreshFails() = runTest {
        val repository = ManagementFixtureRepository()
        val controller = InstalledPluginsController(this, repository)
        controller.bind(managementScope())
        runCurrent()
        controller.dispatch(InstalledPluginsAction.BeginRegistration)
        controller.dispatch(InstalledPluginsAction.ChangeRegistrationDirectory("/source/plugin"))
        repository.afterWrite = { repository.failList = true }
        controller.dispatch(InstalledPluginsAction.RegisterLocal)
        runCurrent()
        assertNull(controller.state.value.registrationDirectory)
        assertEquals(PluginMutationReceipt("1"), controller.state.value.mutationReceipt)
        assertIs<PluginLoadState.Error>(controller.state.value.list)
        assertNull(controller.state.value.visiblePlugins)
        controller.close()
    }

    @Test
    fun listFailureOrRevisionFailureNeverEnablesManagementOrBecomesAnEmptySuccess() = runTest {
        val repository = ManagementFixtureRepository().apply { failList = true }
        val controller = InstalledPluginsController(this, repository)
        controller.bind(managementScope())
        runCurrent()
        assertIs<PluginLoadState.Error>(controller.state.value.list)
        assertNull(controller.state.value.visiblePlugins)
        assertEquals(listOf("list"), repository.reads)
        assertFalse(controller.state.value.canManage)
        repository.failList = false
        repository.failRevision = true
        controller.refresh()
        runCurrent()
        assertIs<PluginLoadState.Success<List<InstalledPlugin>>>(controller.state.value.list)
        assertIs<PluginLoadState.Error>(controller.state.value.revision)
        assertFalse(controller.state.value.canManage)
        controller.dispatch(InstalledPluginsAction.BeginRegistration)
        assertNull(controller.state.value.registrationDirectory)
        controller.close()
    }

    @Test
    fun revisionResponsesAreFencedByScopeAndRequestEvenWhenCancellationIsIgnored() = runTest {
        val staleRevision = CompletableDeferred<Unit>()
        val repository = ManagementFixtureRepository().apply {
            beforeRevision = { if (it.generation == 1L) withContext(NonCancellable) { staleRevision.await() } }
        }
        val controller = InstalledPluginsController(this, repository)
        controller.bind(managementScope())
        runCurrent()
        assertFalse(controller.state.value.canManage)
        controller.bind(managementScope(2))
        repository.revision = "fresh"
        runCurrent()
        repository.revision = "stale"
        staleRevision.complete(Unit)
        runCurrent()
        assertEquals(managementScope(2), controller.state.value.scope)
        assertEquals(PluginLoadState.Success("fresh"), controller.state.value.revision)
        assertTrue(controller.state.value.canManage)
        controller.close()
    }

    @Test
    fun readOnlyDefaultAndDeniedManagementAdaptersCannotOpenDialogsOrWrite() = runTest {
        val readOnly = object : InstalledPluginRepository {
            override val canRead = true
            override suspend fun listInstalled(scope: PluginScope) = PluginResult.Success(emptyList<InstalledPlugin>())
            override suspend fun readDetail(scope: PluginScope, id: PluginId) =
                PluginResult.Success(InstalledPluginDetail(InstalledPlugin(id, id.value)))
        }
        val denied = ManagementFixtureRepository().apply { canManage = false }
        listOf(UnavailablePluginRepository, readOnly, denied).forEach { repository ->
            val controller = InstalledPluginsController(this, repository)
            controller.bind(managementScope())
            runCurrent()
            controller.dispatch(InstalledPluginsAction.BeginRegistration)
            controller.dispatch(InstalledPluginsAction.RegisterLocal)
            controller.dispatch(InstalledPluginsAction.RemoveRegistration)
            controller.dispatch(InstalledPluginsAction.SetEnabled(PluginId("fixture"), true))
            assertFalse(controller.state.value.canManage)
            assertFalse(controller.state.value.hasManagementAdapter)
            assertNull(controller.state.value.registrationDirectory)
            controller.close()
        }
        assertTrue(denied.writes.isEmpty())
        assertEquals(listOf("list"), denied.reads)
    }
}

private fun managementScope(generation: Long = 1) = PluginScope(HostId("management-host"), generation)

/** Inventory and commit guard fixtures live only in tests. */
private class ManagementFixtureRepository(seed: Boolean = false) : LocalPluginManagementRepository {
    override val canRead = true
    override var canManage = true
    var revision = "0"
    var acceptedScope: PluginScope? = null
    var failList = false
    var failDetail = false
    var failRevision = false
    var beforeWrite: suspend () -> Unit = {}
    var afterWrite: () -> Unit = {}
    var beforeRevision: suspend (PluginScope) -> Unit = {}
    val reads = mutableListOf<String>()
    val writes = mutableListOf<Write>()
    var plugins: List<InstalledPlugin> = if (seed) listOf(plugin("/source/plugin")) else emptyList()
    private var commits = 0

    data class Write(val operation: String, val scope: PluginScope, val revision: String)
    override fun bindAccess(binding: PluginAccessBinding?) { acceptedScope = binding?.scope }
    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> {
        reads += "list"
        return if (failList) PluginResult.Error(PluginRepositoryError("list refresh failed")) else PluginResult.Success(plugins)
    }
    override suspend fun readRevision(scope: PluginScope): PluginResult<String> {
        reads += "revision"
        beforeRevision(scope)
        return if (failRevision) PluginResult.Error(PluginRepositoryError("revision failed")) else PluginResult.Success(revision)
    }
    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> {
        reads += "detail"
        if (failDetail) return PluginResult.Error(PluginRepositoryError("detail refresh failed"))
        val plugin = plugins.firstOrNull { it.id == id } ?: return PluginResult.Error(PluginRepositoryError("not registered"))
        return PluginResult.Success(InstalledPluginDetail(plugin, apps = PluginAppsState.Unavailable("Apps 未接入。"),
            skills = PluginSkillsState.Available(listOf(PluginSkillSummary("Review", "Review source text"))),
            unsupportedAssets = listOf("Commands 尚未支持。")))
    }
    override suspend fun registerLocal(scope: PluginScope, directory: String, expectedRevision: String) =
        write("register", scope, expectedRevision) { plugins = plugins + plugin(directory) }
    override suspend fun setEnabled(scope: PluginScope, id: PluginId, enabled: Boolean, expectedRevision: String) =
        write(if (enabled) "enable" else "disable", scope, expectedRevision) {
            plugins = plugins.map { if (it.id == id) it.copy(enabled = if (enabled) PluginEnabledState.Enabled else PluginEnabledState.Disabled) else it }
        }
    override suspend fun removeRegistration(scope: PluginScope, id: PluginId, expectedRevision: String) =
        write("remove", scope, expectedRevision) { plugins = plugins.filterNot { it.id == id } }
    private suspend fun write(operation: String, scope: PluginScope, expectedRevision: String, commit: () -> Unit): PluginResult<PluginMutationReceipt> {
        writes += Write(operation, scope, expectedRevision)
        beforeWrite()
        if (acceptedScope != null && acceptedScope != scope) return PluginResult.Error(PluginRepositoryError("scope replaced"))
        if (revision != expectedRevision) return PluginResult.Error(PluginRepositoryError("CAS revision mismatch"))
        commit()
        revision = (++commits).toString()
        afterWrite()
        return PluginResult.Success(PluginMutationReceipt(revision))
    }
    private fun plugin(directory: String) = InstalledPlugin(PluginId("fixture"), "Fixture plugin",
        source = directory, enabled = PluginEnabledState.Disabled)
}
