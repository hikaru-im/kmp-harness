package im.hikaru.harness

import android.app.Application
import android.content.Context

/** Selects kotlin-logging's native Logcat backend before any logger is created. */
class HarnessApplication : Application() {

    override fun attachBaseContext(base: Context) {
        System.setProperty("kotlin-logging-to-android-native", "true")
        System.setProperty("kotlin-logging.logStartupMessage", "false")
        super.attachBaseContext(base)
    }
}
