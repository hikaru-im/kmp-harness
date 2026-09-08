# Local Credentials

`credentials-local` resolves credentials in this order: process environment, managed credentials file, project `.env`,
then the Host-provided user `.env`. Its runtime configuration contains final paths and never resolves a platform home.

Desktop injects `HarnessHome/.credentials.yaml` and rejects patch-level storage path overrides. The managed credentials
file is not created at startup; the first managed credential write creates it with owner-only permissions.
