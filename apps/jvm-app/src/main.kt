import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.bundle.desktop.startDesktopProfile
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.desktop.connection.LocalConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main(args: Array<String>) {
    val options = DesktopLaunchOptions.parse(args)
    val profiledHost =
        runBlocking {
            startDesktopProfile(
                harnessHome = options.home,
                overlays = options.patches,
            )
        }
    val harnessHost = profiledHost.host
    val connection = LocalConnection(harnessHost.gateway)

    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "KMP Harness",
            ) {
                HostScreen(connection)
            }
        }
    } finally {
        runBlocking {
            profiledHost.close()
        }
    }
}

private data class DesktopLaunchOptions(
    val home: Path? = null,
    val patches: List<Path> = emptyList(),
) {
    companion object {
        fun parse(arguments: Array<String>): DesktopLaunchOptions {
            var home: Path? = null
            val patches = mutableListOf<Path>()
            var index = 0
            while (index < arguments.size) {
                val argument = arguments[index]
                fun value(option: String): String {
                    index += 1
                    require(index < arguments.size) { "$option requires a value" }
                    return arguments[index]
                }
                when {
                    argument == "--home" -> home = Path.of(value(argument))
                    argument.startsWith("--home=") -> home = Path.of(argument.substringAfter('='))
                    argument == "--patch" -> patches.add(Path.of(value(argument)))
                    argument.startsWith("--patch=") -> patches.add(Path.of(argument.substringAfter('=')))
                    else -> error("Unknown Desktop option: $argument")
                }
                index += 1
            }
            return DesktopLaunchOptions(home, patches)
        }
    }
}

@Composable
private fun HostScreen(connection: Connection) {
    var state by remember { mutableStateOf<HostScreenState>(HostScreenState.Loading) }

    LaunchedEffect(connection) {
        state =
            try {
                val result = connection.host.describe()
                val description = result.data

                if (result.isSuccess && description != null) {
                    HostScreenState.Ready(description)
                } else {
                    HostScreenState.Failed(result.msg ?: "Host 描述请求失败")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                HostScreenState.Failed("Host 描述请求失败")
            }
    }

    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    when (val current = state) {
                        HostScreenState.Loading -> "正在启动 Harness Host"
                        is HostScreenState.Ready ->
                            "${current.description.displayName}\n${current.description.hostId}"
                        is HostScreenState.Failed -> current.message
                    }
            )
        }
    }
}

private sealed interface HostScreenState {
    data object Loading : HostScreenState

    data class Ready(
        val description: HostDescription,
    ) : HostScreenState

    data class Failed(
        val message: String,
    ) : HostScreenState
}
