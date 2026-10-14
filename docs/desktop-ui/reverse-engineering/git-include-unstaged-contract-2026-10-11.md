# Git commit: "include unstaged changes" stage step (Batch AO contract)

Date: 2026-10-11. Follows `git-commit-implementation-contract-2026-10-07.md` (C1).

## 1. What the original does

The C1 contract already recorded the original commit dialog and worker:

- **C02** (`git-branch-switcher`) — the commit dialog renders an **Include unstaged changes** checkbox
  beside the message and the commit actions.
- **C03** (`git-action-new-branch-field`) — the field `git-action-include-unstaged-changes` defaults
  to true and is sent as `includeUnstaged` on the `method:commit` mutation.
- **C04** (worker) — when `includeUnstaged` is true the worker first runs a stage helper (`add -A`
  fallback), then `commit -m`.

The current on-disk bundle still carries the same shape (`{cwd, hostConfig, includeUnstaged:!0}` in
the local git-actions query, `git-action-include-unstaged-changes` form field). C1 deliberately
narrowed to "commit the existing index only" and left no checkbox consumer. This batch adds the
stage step behind an explicit checkbox.

## 2. Contract change

| Symbol | Change |
| --- | --- |
| `GitCommitRequest` | new `includeUnstaged: Boolean = false` |
| `GitCommitReceipt` | new `stagedByRequest: Boolean = false`, `stagedIndexDigest: String = treeDigest` |
| `GitState` | new `includeUnstaged: Boolean = false`; `commitDisabledReason` allows an empty index when the flag is set but the worktree has changes |
| `GitController.setIncludeUnstaged(Boolean)` | readiness-only toggle, blocked while a mutation is in flight |
| `DesktopGitBackend.commit` | when `includeUnstaged` is true, run one `git add -A -- .` step and commit/verify against the **post-stage** index |

The committed tree is still verified against a *real* index read: `stagedIndexDigest` is the digest
the commit was built from, and the controller requires `receipt.treeDigest == receipt.stagedIndexDigest
== after.indexDigest`. For a request without the flag, `stagedByRequest` must stay false and
`stagedIndexDigest` must equal the accepted snapshot digest, so the C1 guarantee is unchanged.

## 3. Safety and ordering

1. The accepted snapshot is re-verified against the current index (`sameCommitSelection`) **before**
   the stage step, so a competing stage cannot be overwritten.
2. The stage step runs before dispatch. A failure there is a `Rejected` result, never
   `UnknownOutcome`; the controller re-reads the real index, so the UI shows the true staged state.
3. If staging leaves the index empty the request is rejected (`没有可提交的变更`) without invoking
   `git commit`.
4. Fixed policy is unchanged: `core.hooksPath=/dev/null`, `commit.gpgSign=false`, no amend, no
   empty commit, no push, no signing. Only one stage step is added.

## 4. Deliberate deviation

The original checkbox defaults to **checked**. This batch defaults to **unchecked** so the existing
explicit-index-only policy text stays truthful until the user opts in. That default is a product
decision, not evidence of original behaviour, and is called out in the plan row.

## 5. Acceptance

- `desktop`-layer (real git, `apps/jvm-app`): stage step commits tracked + untracked and keeps
  ignored files out; a clean worktree is rejected before any command; a stale index is rejected
  before staging with index bytes unchanged.
- `shared`: an empty index is committable only with the flag; the request carries the flag; a receipt
  that omits the stage declaration is not trusted; a receipt binding the post-stage digest verifies.
- `apps/jvm-app` UI: the checkbox is a real consumer — the dialog's submit stays disabled until it is
  checked, and the resulting commit contains the worktree and untracked content.

## 6. Boundaries

- Not covered: per-path stage/unstage, `includeUntracked` as a separate option, push, message
  generation, attribution trailers, the original dialog's width/visual parity, and the original
  default-checked state.
- `git add -A -- .` stages the whole worktree root; ignored files stay ignored.
