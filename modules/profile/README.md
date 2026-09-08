# Profile

`profile` owns the platform-neutral subset of DSH profile composition:

```text
empty root
  -> compiled bundle patches in manifest order
  -> HarnessHome/cordis.patch.yml
  -> launch --patch overlays
  -> launcher-derived patches
```

All layers are flattened before applying. A later patch can therefore target an entry inserted by any earlier layer.
`config` replacement is whole-value replacement, not a deep merge. Missing targets and failed name assertions produce
warnings and are skipped, matching DSH include semantics.

The KMP Runtime currently has no portable equivalent for Cordis group/inject/intercept/isolate or JavaScript
expressions. File codecs reject those fields instead of changing their meaning.
