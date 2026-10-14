# Reference state matrix

`unverified` means the state is specified for capture but has not been compared
with a Codex Desktop screenshot in this repository.

| ID | State | Fixture | Sidebar | Focus | Scroll | 1440×900 | 1280×800 | 1024×768 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| empty | Empty conversation | `empty-session` | expanded | composer | top | captured | captured | captured |
| conversation | Normal conversation | `normal-session` | expanded | none | bottom | unverified | unverified | unverified |
| streaming | Streaming generation | `streaming-session` | expanded | stop action | bottom | unverified | unverified | unverified |
| tool | Expanded tool call | `tool-call` | expanded | tool card | bottom | unverified | unverified | unverified |
| error | Failed request | `error-session` | expanded | error action | bottom | unverified | unverified | unverified |
| no-host | No Host | `no-host` | expanded | host action | top | unverified | unverified | unverified |
| login | Login | `login` | collapsed | mobile field | top | captured | unverified | unverified |
| settings | Settings | `settings` | expanded | first setting | top | captured | unverified | unverified |
| sidebar-collapsed | Conversation with collapsed sidebar | `normal-session` | collapsed | none | bottom | unverified | unverified | unverified |
| narrow | Narrow-window conversation | `normal-session` | temporarily expanded | composer | bottom | n/a | unverified | unverified |

Each state also requires the interaction variants listed in
`interaction-checklist.md`. A single settled screenshot does not cover those
variants.

`captured` means an implementation capture exists under
`captures/2026-10-05-shell-refactor/`. It does not mean pixel alignment with
Codex is verified; the structural comparison and open differences are recorded
in that directory's `README.md`.
