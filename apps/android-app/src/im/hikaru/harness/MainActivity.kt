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
import im.hikaru.harness.client.app.HarnessApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var account: MemberAccount

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = platformAccountEngine()
        val client = createAccountHttpClient(engine)
        account = MemberAccount(
            transport = KtorMemberTransport(client, engine),
            store = AndroidAppSessionStore(applicationContext),
        )
        setContent {
            HarnessApp(connection = null, account = account)
        }
    }

    override fun onDestroy() {
        lifecycleScope.launch { account.shutdown() }
        super.onDestroy()
    }
}
