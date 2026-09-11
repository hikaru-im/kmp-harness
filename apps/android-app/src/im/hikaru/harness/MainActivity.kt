package im.hikaru.harness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.hikaru.harness.client.account.AndroidAppSessionStore
import im.hikaru.harness.client.account.KtorMemberTransport
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.account.createAccountHttpClient
import im.hikaru.harness.client.account.platformAccountEngine
import im.hikaru.harness.client.app.RemoteHarnessApp
import im.hikaru.harness.client.connection.RemoteConnectionOwner
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var account: MemberAccount
    private lateinit var remote: RemoteConnectionOwner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = platformAccountEngine()
        val client = createAccountHttpClient(engine)
        account = MemberAccount(
            transport = KtorMemberTransport(client, engine),
            store = AndroidAppSessionStore(applicationContext),
        )
        val remoteEngine = platformAccountEngine()
        val remoteClient = createAccountHttpClient(remoteEngine)
        remote = RemoteConnectionOwner(
            client = remoteClient,
            closeClient = { remoteClient.close(); remoteEngine.close() },
            scope = lifecycleScope,
        )
        setContent {
            RemoteHarnessApp(account = account, remote = remote, clientId = "android-${hashCode()}")
        }
    }

    override fun onDestroy() {
        lifecycleScope.launch {
            remote.close()
            account.shutdown()
        }
        super.onDestroy()
    }
}
