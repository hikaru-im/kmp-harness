package im.hikaru.harness.client.account

import io.ktor.client.engine.cio.CIO

fun platformAccountEngine() = CIO.create()
