package im.hikaru.harness.client.account

import io.ktor.client.engine.okhttp.OkHttp

fun platformAccountEngine() = OkHttp.create()
