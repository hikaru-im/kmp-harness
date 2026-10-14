# Capture environment

Use this sheet for every reference or golden capture. A capture is valid only
when all fields in the table are recorded beside the image.

| Field | Required value | Recorded value |
| --- | --- | --- |
| Operating system | macOS | unverified |
| Codex Desktop version | exact release/build | unverified |
| Harness build | git revision and build command | unverified |
| Theme | recorded per capture (light or dark) | system follows host; light on this machine |
| Display scale | 100% | locked |
| Window size | 1440×900, 1280×800, or 1024×768 | fixture-defined |
| Font family | system UI family | unverified |
| Font version | installed font version | unverified |
| Locale | en-US | locked |
| Reduced motion | off | locked |
| Pointer | mouse available | locked |
| Scroll position | fixture-defined | fixture-defined |

## Capture procedure

1. Quit other windows that can cover the target application.
2. Set macOS display scaling to 100% and select the dark appearance.
3. Set the exact window size from the matrix without maximizing the window.
4. Load the named fixture and wait for the listed settled state.
5. Record the active focus target, pointer state, sidebar mode, and scroll position.
6. Capture the whole window and keep the fixture name beside the image.

The current repository does not have access to a verified Codex Desktop image
export. The environmental values that depend on that export remain explicitly
`unverified` above.
