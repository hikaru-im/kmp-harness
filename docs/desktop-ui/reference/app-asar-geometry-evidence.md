# app.asar geometry and light-token evidence

Read-only measurements used to correct the Desktop shell geometry and to build the light theme.
Extraction ran in a `mktemp -d /tmp/...` directory; nothing was written into the repository root.

## Baseline

| Field | Value |
| --- | --- |
| Archive | `/Applications/ChatGPT.app/Contents/Resources/app.asar` |
| App version | `26.930.31730` |
| SHA-256 | `64023959c7c3a4c19022c44f3e8d4b3e4010cefb6e2ea1edce0b535fd417776e` |
| Size | 546863104 bytes |

Extracted for this pass: `codex-light-53d4e35e1869.js`, `codex-dark-5fb95d1c65ca.js`,
`app-shared-6fb15e58cd7f.css`, `thread-app-shell-chrome-ecae05bb1b8f.css`,
`thread-scroll-layout-24b9bd243051.css`, `composer-controls-146a0c4fef05.css`.

## Left navigation is ONE column, not rail + navigator

```css
--spacing-token-sidebar: clamp(240px, var(--codex-sidebar-preferred-width, 275px), min(520px, calc(100vw - 320px)));
.w-token-sidebar { width: var(--spacing-token-sidebar); }
.ps-token-sidebar { padding-inline-start: var(--spacing-token-sidebar); }
```

There is no rail class, no `w-token-rail`, and the main content is inset by the same sidebar token.
The previous `App Rail 64px + Navigator 275px` model (339px total) was wrong; 275px is the whole
left navigation region.

## Other measured geometry

| Token | Value | Meaning |
| --- | --- | --- |
| `--spacing` | 0.25rem (4px) | base unit |
| `--height-titlebar` | calc(spacing * 11) = 44px | titlebar |
| `--height-toolbar` | 46px | toolbar |
| `--height-toolbar-sm` | 36px | compact toolbar |
| `--height-token-row` | 36px | nav/row height |
| `--height-token-nav-row` | 30px / 36px | nav row variant |
| `--icon-leading-size` | calc(spacing * 5) = 20px | leading icon |
| `--thread-content-max-width` | 42rem = 672px (48rem expanded) | thread body width |
| `--thread-content-compact-max-width` | 40rem = 640px | compact body width |
| `--composer-radius` | calc(spacing * 5.5) = 22px | composer radius |
| `--composer-gutter` | calc(spacing * 3) = 12px | composer gutter |
| `--right-panel-composer-overlay-reserve` | 118px | right panel/composer reserve |
| `--radius-xs/sm/md/lg/xl/2xl` | 4 / 6 / 8 / 8–10 / 12 / 16px | radii |
| `--font-ui-weight` | 430 | UI font weight |

`thread-scroll-layout.css` confirms the body width is driven by `--thread-content-max-width` plus
`--padding-toolbar`, and `thread-app-shell-chrome.css` shows rows separated by
`1px solid var(--color-border-subtle)`.

## Light palette inputs

| Token | Light value | Use |
| --- | --- | --- |
| `--gray-fixed-0` | `#ffffff` | canvas / elevated |
| `--gray-fixed-50` | `#f9f9f9` | sidebar surface |
| `--gray-fixed-75` | `#f3f3f3` | secondary surface |
| `--gray-fixed-100` | `#ededed` | selected row |
| `--gray-fixed-400` | `#8f8f8f` | disabled text |
| `--gray-fixed-500` / `--gray-500` | `#5d5d5d` | secondary text |
| `--gray-fixed-1000` | `#0d0d0d` | primary text |
| `--alpha-05` | `#7f7f7f14` | subtle border |
| `--alpha-10` | `#7f7f7f29` | border |
| `--blue-500` | `#0169cc` | accent / focus |
| `--green-500` | `#00a240` | success / diff added |
| `--red-500` | `#e02e2a` | danger / diff removed |
| `--yellow-500` | `#e0ac00` | warning |
| `--color-text-secondary` | light `--gray-500` | muted text |
| `--font-openai-sans` | `"OpenAI Sans", -apple-system...` | UI font (fallback used in Compose) |

`--color-surface` has several conflicting definitions in app-shared; the values above follow the
observed light rendering (white canvas, `#f9f9f9` sidebar) rather than a single late override.

## Type scale

`--font-text-xs` 12/16, `--font-text-sm` 14/18, `--font-text-md` 16/24, `--font-text-lg` 18/28,
`--font-heading-xs` 16/24, `--font-heading-sm` 18/26, `--font-heading-md` 20/26.

## Screenshots produced from this baseline

| File | State |
| --- | --- |
| `docs/desktop-ui/captures/reference-thread-dark-2560x1440.png` | kmp-harness / 查看项目状态 thread, context panel, composer, dark |
| `docs/desktop-ui/captures/home-dark-2560x1440.png` | Home dark |
| `docs/desktop-ui/captures/account-dark-2560x1440.png` | Account dark |

Rendered by `apps/jvm-app/test/im/hikaru/harness/desktop/ScreenshotTest.kt` via
`ImageComposeScene(size, density = 1f)`. The test writes each shell in both palettes at
2560x1440, 1440x900, 1280x800 and 1024x768 (`*-light-*` and `*-dark-*`). It renders Compose
content only; the native title bar and traffic lights come from
`scripts/capture-desktop-window.sh` (see `docs/desktop-ui/native-window-acceptance.md`).
