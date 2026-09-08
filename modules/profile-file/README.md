# File Profile

`profile-file` implements the JVM filesystem side of DSH-compatible profile boot. It requires a platform-selected
`HarnessHome` and never reads command-line options, environment variables or operating-system defaults.

Each platform chooses one Harness home and does not add a named-profile directory. On Desktop the profile files are
`HarnessHome/package.json`, `HarnessHome/cordis.patch.yml`, and `HarnessHome/cordis.yml`. First use creates
all three at the home root. Existing manifest and patch files are never overwritten.

`package.json` uses the DSH fields `dsh.profile.bundles` and `dsh.profile.patchReload`. `startup` loads once; `live`
watches the manifest, home patch and launch overlays. Parsing, config preflight and Loader reconciliation
complete before a new snapshot becomes current. Failed reloads keep the last successful Fibers active.

Bundle names resolve through a compiled `ProfileBundleCatalog`. This is the cross-platform replacement for Node runtime
package import and does not download or scan code.
