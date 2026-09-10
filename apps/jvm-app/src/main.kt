import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import im.hikaru.harness.bundle.desktop.startDesktopProfile
import im.hikaru.harness.bundle.desktop.resolveDesktopHarnessHome
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.account.KtorMemberTransport
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.account.createAccountHttpClient
import im.hikaru.harness.client.account.desktopAppSessionStore
import im.hikaru.harness.client.account.platformAccountEngine
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.desktop.connection.LocalConnection
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
    val account = MemberAccount(
        transport = KtorMemberTransport(createAccountHttpClient(platformAccountEngine())),
        store = desktopAppSessionStore(resolveDesktopHarnessHome(options.home).directory),
    )

    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "KMP Harness",
            ) {
                HarnessApp(connection = connection, account = account)
            }
        }
    } finally {
        runBlocking {
            account.shutdown()
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
