package im.hikaru.harness.exec.jvm

import java.nio.file.Files
import java.nio.file.Path

/** UTF-8 pinning prepended to every PowerShell command; inherited from DSH's pwsh executor. */
internal const val PWSH_ENCODING_PREAMBLE: String =
    "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(\$false); " +
        "\$OutputEncoding = [System.Text.UTF8Encoding]::new(\$false); "

/**
 * Resolves the PowerShell executable in DSH order: PowerShell 7's well-known
 * install, PATH entries (Microsoft Store aliases included), then Windows
 * PowerShell 5.1. Outside Windows the bare `pwsh` name is left to PATH.
 */
internal object PwshExecutable {
    fun resolve(
        environment: (String) -> String? = System::getenv,
        osName: String = System.getProperty("os.name"),
        exists: (Path) -> Boolean = { Files.isRegularFile(it) || Files.isSymbolicLink(it) },
    ): String {
        if (!osName.startsWith("Windows", ignoreCase = true)) return "pwsh"
        val programFiles = environment("ProgramFiles") ?: "C:\\Program Files"
        val systemRoot = environment("SystemRoot") ?: "C:\\Windows"
        val candidates = buildList {
            add(Path.of(programFiles, "PowerShell", "7", "pwsh.exe"))
            (environment("PATH") ?: "").split(';').forEach { entry ->
                val trimmed = entry.trim().trim('"')
                if (trimmed.isNotEmpty()) add(Path.of(trimmed, "pwsh.exe"))
            }
            // Windows PowerShell 5.1 remains the last-resort fallback on legacy hosts.
            add(Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe"))
        }
        return candidates.firstOrNull(exists)?.toString() ?: "pwsh"
    }
}
