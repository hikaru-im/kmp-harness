package im.hikaru.harness.client.account

import io.ktor.client.engine.darwin.Darwin

fun platformAccountEngine() = Darwin.create()
