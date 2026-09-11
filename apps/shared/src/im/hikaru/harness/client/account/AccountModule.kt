package im.hikaru.harness.client.account

import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module

/** Install into an application-owned KoinApplication, never into the Harness Runtime container. */
fun accountModule(engine: HttpClientEngine, store: AppSessionStore) = module {
    single<AppSessionStore> { store }
    single<MemberTransport> { KtorMemberTransport(createAccountHttpClient(engine), engine) }
    single { MemberAccount(get(), get()) }
}
