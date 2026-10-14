# Common component analysis

公共组件是复刻效率的最高杠杆。页面 bundle 中反复出现的 Button、Menu、Dialog、Popover、Tooltip、Tabs、Input、Switch、List、Section、Row、Toast、Alert、Scroll 和 Resize，应先还原成稳定的 Compose 基础层，再实现具体页面。

## Evidence from app.asar

The selected Codex bundles repeatedly reference these component families:

| Family | Strong evidence | Appears in |
| --- | --- | --- |
| Button and IconButton | onClick, aria-label, disabled, loading, pressed | app-shared, thread shell, projects, settings, sidebar |
| Menu and Dropdown | onSelect, triggerButton, sections, separators, keyboard shortcuts | sidebar, thread shell, projects, settings |
| Dialog and Modal | open, onOpenChange, confirmation, error actions | projects, settings, account, automation flows |
| Popover and Tooltip | trigger, side, align, tooltipContent, delayed open | thread, settings, composer, sidebar |
| Tabs | selectedKey, tab list, switch-tab, nested settings tabs | thread shell, settings, background terminal |
| Input and Textarea | search, prompt, filters, validation, keyboard handling | settings, projects, composer, sidebar |
| Switch and Checkbox | checked, disabled, onChange, preference rows | settings, sidebar, composer |
| List, Section and Row | loading rows, empty states, expandable sections, nested rows | sidebar, projects, thread panels, settings |
| Toast and Alert | retry, success, error, unavailable, destructive action | settings, projects, scheduled tasks, thread |
| Scroll and Resize | scroll recents, panel resize, content layout, compact breakpoints | sidebar, thread shell, settings |

The current scan found these representative assets:

- app-shared-9d148924be0b.js: shared UI runtime and common behavior.
- composer-controls-9cde96680264.js: composer controls and keyboard interaction.
- thread-app-shell-chrome-6e13d1129e78.js: shell actions, tabs, panels, scrolling, and resizing.
- actions-menu-63a20765efcb.js: reusable action menu behavior.
- Tooltip-5f651dfc2803.js: reusable tooltip implementation.
- settings-tabs-9b030b43e026.js: selected-key tab contract and nested settings navigation.
- settings-row-disclosure-e843f6c137ee.js: reusable settings row disclosure behavior.

## Current Kotlin foundation

The project already has these reusable components in modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComponents.kt:

- HarnessSurface
- HarnessText
- HarnessIconButton
- HarnessButton
- HarnessTextField
- HarnessMenu
- HarnessMenuItem
- HarnessDialog
- HarnessTooltip
- HarnessBadge
- HarnessScrollbar

The token layer already contains measured colors, typography, spacing, control heights, sidebar geometry, settings navigator geometry, and rail geometry in HarnessTokens.kt.

## Gaps that affect every page

### 1. Button state contract

The current HarnessButton has Normal, Loading, and Error control states, but the Codex evidence also needs pressed, selected, destructive, compact, icon-only, and menu-trigger variants. The shared contract should expose visual state and action state separately.

### 2. Menu and popover anchoring

The current HarnessMenu is a Material DropdownMenu wrapper. Codex uses anchored menus with alignment, side offsets, trigger ownership, keyboard navigation, separators, and deferred selection after dismissal. This affects the rail More menu, account menu, project menu, usage menu, and composer tools.

### 3. Dialog confirmation flow

Codex dialogs carry open state, close callbacks, loading state, retry actions, destructive confirmation, and sometimes unsaved-change protection. A visual Dialog wrapper is not enough; the action result must be part of the state model.

### 4. Tabs and panel outlets

Settings Worktrees, thread tabs, bottom panel tabs, and Chrome tabs use the same selected-key idea but have different ownership and close behavior. They need one common tab model with optional close, replace, launch, and panel-target fields.

### 5. Row and list states

The same row pattern appears in settings, projects, scheduled tasks, recent tasks, thread artifacts, terminals, and agents. The shared row should support leading content, title, secondary text, trailing action, selected state, disabled state, loading state, error retry, nested indentation, and expansion.

### 6. Feedback states

Loading, error, empty, unavailable, retry, and success appear across almost every bundle. These should be modeled as reusable state content instead of page-specific text and spacing.

### 7. Scroll and resize behavior

The shell has scrollable recents, scrollable settings, bottom panels, right panels, and a resizable navigator. These behaviors affect layout state and cannot be implemented only with a visual scrollbar.

## Recommended implementation order

1. Add a typed HarnessActionButton and HarnessIconButton state model.
2. Add anchored HarnessPopover and HarnessMenu with menu item, separator, disabled, loading, and keyboard states.
3. Add HarnessTabs and HarnessTab with selected-key and optional close or launch actions.
4. Add HarnessRow, HarnessSection, HarnessEmptyState, HarnessErrorState, and HarnessLoadingState.
5. Add HarnessSwitch and HarnessCheckbox with the measured Codex control geometry.
6. Add a panel state model for right panel, bottom panel, and resizable navigator.
7. Migrate Settings General, Projects, Scheduled Tasks, and Thread to these components.

This order gives the next pages real behavior and keeps screenshot changes localized to the shared component layer.
