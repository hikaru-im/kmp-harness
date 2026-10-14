# Desktop UI reference package

This directory freezes the inputs for the first visual comparison pass of the
KMP Harness Desktop shell. It is a reproducible reference contract, not a
claim that the Codex Desktop pixels have already been measured.

## Reference environment

| Input | Locked value |
| --- | --- |
| Platform | macOS, desktop build under test |
| Theme | Dark |
| Display scale | 100% |
| Font | System UI font reported by the reference machine; exact family and version must be recorded before golden capture |
| Window sizes | 1440×900, 1280×800, 1024×768 |
| Locale | `en-US` for reference captures; localized copy is a separate coverage pass |
| Scroll position | Fixture-defined; `top` unless the matrix says otherwise |

`environment.md` contains the capture checklist and the fields that must be
filled from the machine producing the screenshots. Until those fields and the
reference screenshots are present, pixel alignment is unverified.

## Package contents

- `environment.md`: capture environment, scaling, font, and window checklist.
- `state-matrix.md`: required screens and interaction states for each target size.
- `fixtures/desktop-reference-fixtures.json`: stable data and scroll fixtures.
- `interaction-checklist.md`: hover, focus, keyboard, loading, error, and drag states.

The JSON fixture is intentionally independent from runtime APIs. It gives
Compose previews, screenshot tests, and manual captures the same visible data.

## Verification boundary

The repository currently contains no Codex Desktop screenshot export or measured
first-frame geometry. The rows marked `unverified` must stay that way until a
human capture or an approved reference image is added. Do not turn those rows
into golden expectations by inference.
