import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import im.hikaru.harness.client.account.IosAppSessionStore
import im.hikaru.harness.client.account.KtorMemberTransport
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.account.createAccountHttpClient
import im.hikaru.harness.client.account.platformAccountEngine
import im.hikaru.harness.client.app.RemoteHarnessApp
import im.hikaru.harness.client.connection.RemoteConnectionOwner
import kotlinx.coroutines.launch

fun ViewController() = ComposeUIViewController {
    val scope = rememberCoroutineScope()
    val account = remember {
        val engine = platformAccountEngine()
        val client = createAccountHttpClient(engine)
        MemberAccount(
            transport = KtorMemberTransport(client, engine),
            store = IosAppSessionStore(),
        )
    }
    val remote = remember {
        val engine = platformAccountEngine()
        val client = createAccountHttpClient(engine)
        RemoteConnectionOwner(
            client = client,
            closeClient = { client.close(); engine.close() },
            scope = scope,
        )
    }
    DisposableEffect(account) {
        onDispose { scope.launch { account.shutdown() } }
    }
    RemoteHarnessApp(account = account, remote = remote, clientId = "ios-client")
}
