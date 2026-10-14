package im.hikaru.harness.client.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

public interface ShortcutsStore {
    public val durable: Boolean
    public suspend fun read(): ShortcutsSettings?
    public suspend fun write(settings: ShortcutsSettings)
}

public class MemoryShortcutsStore(initial: ShortcutsSettings? = null) : ShortcutsStore {
    override val durable: Boolean = false
    private var value = initial
    override suspend fun read(): ShortcutsSettings? = value
    override suspend fun write(settings: ShortcutsSettings) { value = settings }
}

public enum class ShortcutCaptureMode { None, Recording, Search }

public data class ShortcutEditor(
    val command: ShortcutCommand,
    val replaceIndex: Int? = null,
    val chord: ShortcutChord? = null,
    val error: String? = null,
)

public data class ShortcutsState(
    val settings: ShortcutsSettings = ShortcutsSettings(),
    val platform: ShortcutPlatform = ShortcutPlatform.Unavailable,
    val durable: Boolean = false,
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val draft: ShortcutsSettings? = null,
    val editor: ShortcutEditor? = null,
    val capture: ShortcutCaptureMode = ShortcutCaptureMode.None,
    val keyQuery: ShortcutChord? = null,
    val captureError: String? = null,
    val resetConfirmation: Boolean = false,
    val saveRevision: Long = 0,
) {
    public val canEdit: Boolean get() = platform != ShortcutPlatform.Unavailable && loaded && !loading && !saving && loadError == null
    public val dispatchBlocked: Boolean get() = capture != ShortcutCaptureMode.None || editor != null || resetConfirmation || saving
    public val unavailableReason: String? get() = if (platform == ShortcutPlatform.Unavailable) "当前平台尚未接入按键适配器；快捷键编辑和窗口关闭命令不可用。" else null
}

/** One effective snapshot is shared by the Settings page, App and native window consumer. */
public class ShortcutsRepository(
    private val store: ShortcutsStore = MemoryShortcutsStore(),
    public val platform: ShortcutPlatform = ShortcutPlatform.Unavailable,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ShortcutsState(platform = platform, durable = store.durable))
    public val state: StateFlow<ShortcutsState> = mutableState.asStateFlow()
    public val input: ShortcutsInputController = ShortcutsInputController(this)

    public suspend fun load() {
        if (!mutex.tryLock()) return
        try {
            mutableState.value = mutableState.value.copy(loading = true, loadError = null)
            val candidate = store.read() ?: ShortcutsSettings()
            require(ShortcutRegistry.validationError(candidate, platform) == null)
            mutableState.value = mutableState.value.copy(settings = candidate.snapshot(), loaded = true, loading = false,
                saveError = null, draft = null, editor = null, capture = ShortcutCaptureMode.None)
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(loading = false)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(loading = false, loadError = "无法读取快捷键配置。文件可能损坏、版本不支持或存在冲突；原文件保持不变。")
        } finally { mutex.unlock() }
    }

    public suspend fun save(candidate: ShortcutsSettings): Boolean {
        if (!mutableState.value.canEdit || !mutex.tryLock()) return false
        val snapshot = candidate.snapshot()
        try {
            val error = ShortcutRegistry.validationError(snapshot, platform)
            if (error != null) {
                mutableState.value = mutableState.value.copy(saveError = error, draft = snapshot)
                return false
            }
            mutableState.value = mutableState.value.copy(saving = true, saveError = null, draft = snapshot)
            currentCoroutineContext().ensureActive()
            // Cancellation cannot separate an atomic file commit from publication of its mapping.
            withContext(NonCancellable) {
                store.write(snapshot)
                mutableState.value = mutableState.value.copy(settings = snapshot, saving = false, draft = null, editor = null,
                    capture = ShortcutCaptureMode.None, resetConfirmation = false, saveRevision = mutableState.value.saveRevision + 1)
            }
            currentCoroutineContext().ensureActive()
            return true
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(saving = false)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(saving = false, saveError = "无法保存快捷键配置，请重试。旧快捷键仍然有效，草稿已保留。")
            return false
        } finally { mutex.unlock() }
    }

    public suspend fun retry() {
        val current = mutableState.value
        if (!current.loaded || current.loadError != null) load() else current.draft?.let { save(it) }
    }

    public fun beginRecording(command: ShortcutCommand, replaceIndex: Int? = null) {
        val current = mutableState.value
        if (!current.canEdit || current.resetConfirmation) return
        if (replaceIndex != null && replaceIndex !in current.settings.bindings(command, platform).indices) return
        mutableState.value = current.copy(editor = ShortcutEditor(command, replaceIndex), capture = ShortcutCaptureMode.Recording,
            keyQuery = null, captureError = null, saveError = null, draft = null)
    }

    public fun record(chord: ShortcutChord?) {
        val current = mutableState.value
        if (current.saving) return
        if (current.capture == ShortcutCaptureMode.Search) {
            mutableState.value = current.copy(keyQuery = chord, captureError = if (chord == null) "此按键尚未支持。" else null)
            return
        }
        val editor = current.editor ?: return
        if (current.capture != ShortcutCaptureMode.Recording || !current.canEdit) return
        val error = if (chord == null) "此按键尚未支持；只支持一组组合键。" else ShortcutRegistry.chordError(chord)
        val recorded = editor.copy(chord = chord, error = error)
        val candidateError = if (error == null) editorCandidate(recorded)?.let { ShortcutRegistry.validationError(it, platform) } else error
        mutableState.value = current.copy(editor = recorded.copy(error = candidateError),
            capture = if (candidateError == null) ShortcutCaptureMode.None else ShortcutCaptureMode.Recording)
    }

    public fun recordModifierOnly() {
        val current = mutableState.value
        if (current.capture == ShortcutCaptureMode.Recording && !current.saving) {
            mutableState.value = current.copy(editor = current.editor?.copy(error = "请再按一个非修饰键。"))
        }
    }

    private fun editorCandidate(editor: ShortcutEditor): ShortcutsSettings? {
        val chord = editor.chord ?: return null
        val current = mutableState.value.settings
        val bindings = current.bindings(editor.command, platform).toMutableList()
        if (editor.replaceIndex == null) bindings.add(chord)
        else if (editor.replaceIndex in bindings.indices) bindings[editor.replaceIndex] = chord else return null
        return current.withBindings(editor.command, bindings)
    }

    public suspend fun saveEditor(): Boolean {
        val editor = mutableState.value.editor ?: return false
        if (editor.error != null) return false
        return editorCandidate(editor)?.let { save(it) } ?: false
    }

    public fun cancelCapture() {
        if (mutableState.value.saving) return
        mutableState.value = mutableState.value.copy(editor = null, capture = ShortcutCaptureMode.None, captureError = null,
            draft = null, saveError = null)
    }

    public fun startKeySearch() {
        if (platform == ShortcutPlatform.Unavailable || mutableState.value.saving || mutableState.value.editor != null || mutableState.value.resetConfirmation) return
        mutableState.value = mutableState.value.copy(capture = ShortcutCaptureMode.Search, keyQuery = null, captureError = null)
    }

    public fun stopKeySearch() {
        if (mutableState.value.saving) return
        mutableState.value = mutableState.value.copy(capture = ShortcutCaptureMode.None, keyQuery = null, captureError = null)
    }

    public fun requestReset() {
        if (mutableState.value.canEdit && mutableState.value.editor == null) {
            mutableState.value = mutableState.value.copy(resetConfirmation = true, capture = ShortcutCaptureMode.None,
                keyQuery = null, captureError = null)
        }
    }

    public fun cancelReset() {
        if (!mutableState.value.saving) mutableState.value = mutableState.value.copy(resetConfirmation = false, draft = null, saveError = null)
    }

    public suspend fun resetAll(): Boolean = if (mutableState.value.resetConfirmation) save(ShortcutsSettings()) else false

    public suspend fun remove(command: ShortcutCommand, index: Int): Boolean {
        val current = mutableState.value.settings
        val bindings = current.bindings(command, platform).toMutableList()
        if (index !in bindings.indices) return false
        bindings.removeAt(index)
        return save(current.withBindings(command, bindings))
    }

    public suspend fun reset(command: ShortcutCommand): Boolean = save(mutableState.value.settings.reset(command))

    public fun commandFor(chord: ShortcutChord): ShortcutCommand? {
        val current = mutableState.value
        if (!current.loaded || current.loading || current.loadError != null || platform == ShortcutPlatform.Unavailable) return null
        return ShortcutRegistry.commands.firstOrNull { chord in current.settings.bindings(it, platform) }
    }
}

private fun ShortcutsSettings.snapshot(): ShortcutsSettings = copy(overrides = overrides.mapValues { it.value.toList() })
