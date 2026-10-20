package im.hikaru.harness.session.api

import im.hikaru.harness.runtime.service.ServiceKey

/**
 * The Host process's own working directory, resolved by the platform that starts the Host.
 *
 * DSH falls back to `process.cwd()` when a request names neither a workspace nor a cwd. A Host that
 * cannot name a directory (tests, non-desktop embedders) leaves the key absent and Sessions keep a
 * null cwd instead of inventing one.
 */
public object SessionDefaultCwdKey : ServiceKey<String>("session-default-cwd")
