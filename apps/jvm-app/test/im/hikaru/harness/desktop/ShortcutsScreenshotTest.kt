package im.hikaru.harness.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.page.SettingsShortcutsPage
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

/** Structural captures only: no original Shortcuts screenshot exists for pixel comparison. */
class ShortcutsScreenshotTest {
    @Test
    fun captureDefaultRecordingAndKeySearchAtContractSizesInBothThemes() {
        val directory = System.getProperty("harness.shortcuts.captureDir")?.let(::File)
            ?: Files.createTempDirectory("harness-shortcuts-captures-").toFile()
        directory.mkdirs()
        listOf(420, 600, 1440).forEach { width ->
            listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark).forEach { mode ->
                listOf("default", "recording", "search").forEach { state ->
                    val repository = ShortcutsRepository(platform = ShortcutPlatform.Mac)
                    runBlocking { repository.load() }
                    when (state) {
                        "recording" -> repository.beginRecording(ShortcutCommand.Settings, 0)
                        "search" -> {
                            repository.startKeySearch()
                            repository.record(ShortcutChord(ShortcutKey.W, meta = true))
                        }
                    }
                    val file = File(directory, "shortcuts-" + state + "-" + mode.name.lowercase() + "-" + width + "x900.png")
                    render(file, width, 900) { HarnessTheme(mode) { SettingsShortcutsPage(repository) } }
                    val image = requireNotNull(ImageIO.read(file))
                    assertEquals(width, image.width)
                    assertEquals(900, image.height)
                    assertTrue(file.length() > 0)
                }
            }
        }
        println("SHORTCUTS STRUCTURAL CAPTURES: " + directory.absolutePath)
    }

    private fun render(file: File, width: Int, height: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f), content = content)
        try {
            scene.render()
            val image = scene.render()
            file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        } finally { scene.close() }
    }
}
