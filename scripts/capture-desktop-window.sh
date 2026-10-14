#!/usr/bin/env bash
#
# Capture the real macOS window of jvm-app: native title bar, traffic lights and app content.
#
# The offscreen Compose screenshot test (ScreenshotTest) can only render app content because the
# macOS title bar and traffic lights are drawn by the OS. This script launches jvm-app in an
# isolated Harness home, waits for its window, resolves it through CoreGraphics and captures
# that window by ID, so the PNG includes the native chrome and nothing behind the window.
#
# Usage:
#   scripts/capture-desktop-window.sh [--size 1440x900] [--destination harness] [--theme system]
#                                     [--out docs/desktop-ui/captures/native] [--name PREFIX]
#                                     [--account-menu-open] [--keep]
#
# Options:
#   --size WxH          Window size in points/logical pixels (default 1440x900).
#   --destination NAME  HarnessDestination launch value: harness/home/account (default harness).
#   --theme MODE        system | light | dark (default system: follow the macOS appearance).
#   --out DIR           Output directory for the PNG (default docs/desktop-ui/captures/native).
#   --name PREFIX       Output file name prefix (default desktop-DEST-THEME-SIZE).
#   --account-menu-open Start with the account avatar menu open (acceptance capture only).
#   --build-output DIR  Optional separate build output directory (avoids locked repo build dir).
#   --timeout SECONDS   How long to wait for the window (default 900; first run compiles).
#   --keep              Leave the app running after the capture instead of closing it.
#
# Requirements / limits:
#   * macOS with a logged-in GUI session. It cannot run headless or over plain SSH without a
#     console session.
#   * The process running this script needs Screen Recording permission (for screencapture).
#     The window is located with CoreGraphics, so System Events automation is not required.
#     Without Screen Recording permission the script fails loudly.
#   * Window bounds are read from the main display origin. Multi-display or negative coordinates
#     may need a manual screencapture -R / -w fallback (printed on failure).
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SIZE="1440x900"
DEST="harness"
THEME="system"
OUT_REL="docs/desktop-ui/captures/native"
NAME=""
KEEP=0
ACCOUNT_MENU_OPEN=0
APP_PID=""
BUILD_OUTPUT=""
WINDOW_TIMEOUT=900

while [[ $# -gt 0 ]]; do
  case "$1" in
    --size) SIZE="$2"; shift 2 ;;
    --destination) DEST="$2"; shift 2 ;;
    --theme) THEME="$2"; shift 2 ;;
    --out) OUT_REL="$2"; shift 2 ;;
    --name) NAME="$2"; shift 2 ;;
    --build-output) BUILD_OUTPUT="$2"; shift 2 ;;
    --timeout) WINDOW_TIMEOUT="$2"; shift 2 ;;
    --account-menu-open) ACCOUNT_MENU_OPEN=1; shift ;;
    --keep) KEEP=1; shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 64 ;;
  esac
done

ACCOUNT_MENU_ARGS=()
if [[ "$ACCOUNT_MENU_OPEN" == "1" ]]; then
  ACCOUNT_MENU_ARGS+=(--account-menu-open)
fi

OUT_DIR="$ROOT/$OUT_REL"
mkdir -p "$OUT_DIR"
[[ -n "$NAME" ]] || NAME="desktop-$DEST-$THEME-$SIZE"
PNG="$OUT_DIR/$NAME.png"

HARNESS_TMP_HOME="$(mktemp -d -t kmp-harness-native-home.XXXXXX)"
APP_LOG="$(mktemp -t kmp-harness-native-app.XXXXXX.log)"

cleanup() {
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  rm -rf "$HARNESS_TMP_HOME"
  # WINDOW_LOOKUP is assigned after this trap is installed, so it can still be unset here.
  [ -n "${WINDOW_LOOKUP:-}" ] && rm -f "$WINDOW_LOOKUP"
}
trap cleanup EXIT

echo "Launching jvm-app (size=$SIZE destination=$DEST theme=$THEME home=$HARNESS_TMP_HOME)"
(
  cd "$ROOT"
  if [[ -n "$BUILD_OUTPUT" ]]; then
    ./kotlin --build-output="$BUILD_OUTPUT" run -m jvm-app -- \
      --home "$HARNESS_TMP_HOME" \
      --window-size "$SIZE" \
      --destination "$DEST" \
      --theme "$THEME" ${ACCOUNT_MENU_ARGS[@]+"${ACCOUNT_MENU_ARGS[@]}"}
  else
    ./kotlin run -m jvm-app -- \
      --home "$HARNESS_TMP_HOME" \
      --window-size "$SIZE" \
      --destination "$DEST" \
      --theme "$THEME" ${ACCOUNT_MENU_ARGS[@]+"${ACCOUNT_MENU_ARGS[@]}"}
  fi
) >"$APP_LOG" 2>&1 &
APP_PID=$!

# The window is identified through CoreGraphics (CGWindowID). screencapture -l then reads that
# window's own content, so a window in front of it can never leak into the capture.
WINDOW_LOOKUP="$(mktemp -t kmp-harness-window-id.XXXXXX.swift)"
cat >"$WINDOW_LOOKUP" <<'SWIFT'
import CoreGraphics
import Foundation

let wanted = "KMP Harness"
// Only windows owned by this launch (see the process tree below) may match, so an older jvm-app
// window with the same title is never captured instead.
let allowed = Set(CommandLine.arguments.dropFirst().compactMap { Int($0) })
let onScreen = CGWindowListCopyWindowInfo(
    [.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID
) as? [[String: Any]] ?? []
var best: (number: Int, x: Int, y: Int, width: Int, height: Int, area: Int)?
for window in onScreen {
    guard
        // Window ownership is matched by title only: the JVM window owner name differs between
        // launch modes (java, MainKt, dev tools), so an owner-name assumption never matches.
        let name = window[kCGWindowName as String] as? String, name == wanted,
        let owner = window[kCGWindowOwnerPID as String] as? Int,
        allowed.contains(owner),
        let bounds = window[kCGWindowBounds as String] as? [String: Any],
        let number = window[kCGWindowNumber as String] as? Int
    else { continue }
    let x = Int((bounds["X"] as? Double) ?? 0)
    let y = Int((bounds["Y"] as? Double) ?? 0)
    let width = Int((bounds["Width"] as? Double) ?? 0)
    let height = Int((bounds["Height"] as? Double) ?? 0)
    if best == nil || width * height > best!.area {
        best = (number, x, y, width, height, width * height)
    }
}
if let found = best {
    print(String(found.number) + " " + String(found.x) + " " + String(found.y) + " " + String(found.width) + " " + String(found.height))
}
SWIFT

WINDOW_ID=""
ATTEMPTS=$(( WINDOW_TIMEOUT / 2 ))
for _ in $(seq 1 "$ATTEMPTS"); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "jvm-app exited before a window appeared. Log tail:" >&2
    tail -n 40 "$APP_LOG" >&2
    exit 2
  fi
  # Rebuild the tree on every poll: the launcher may not have spawned its JVM yet.
  APP_TREE="$APP_PID"
  PID_FRONTIER="$APP_PID"
  for _ in 1 2 3 4 5 6; do
    CHILDREN=""
    for parent_pid in $PID_FRONTIER; do
      CHILDREN="$CHILDREN $(pgrep -P "$parent_pid" 2>/dev/null || true)"
    done
    CHILDREN="$(echo "$CHILDREN" | xargs)"
    [[ -z "$CHILDREN" ]] && break
    APP_TREE="$APP_TREE $CHILDREN"
    PID_FRONTIER="$CHILDREN"
  done
  WINDOW_INFO="$(swift "$WINDOW_LOOKUP" $APP_TREE 2>/dev/null || true)"
  if [[ -n "$WINDOW_INFO" ]]; then
    read -r WINDOW_ID PX PY SW SH <<<"$WINDOW_INFO"
    break
  fi
  sleep 2
done

if [[ -z "$WINDOW_ID" ]]; then
  echo "No on-screen window titled 'KMP Harness' appeared within $WINDOW_TIMEOUT s." >&2
  echo "The first run compiles the project and can take several minutes; raise the limit with --timeout." >&2
  echo "This usually means no GUI session is attached, or Screen Recording permission is missing." >&2
  echo "Manual fallback: run the app, focus it, then run: screencapture -w -o $PNG" >&2
  echo "App log tail:" >&2
  tail -n 40 "$APP_LOG" >&2
  exit 3
fi

echo "Window id=$WINDOW_ID bounds=x=$PX,y=$PY,w=$SW,h=$SH"
if ! screencapture -x -o -l "$WINDOW_ID" "$PNG" 2>"$APP_LOG.screencapture"; then
  echo "screencapture failed:" >&2
  cat "$APP_LOG.screencapture" >&2
  exit 4
fi

if [[ ! -s "$PNG" ]]; then
  echo "screencapture produced no image; Screen Recording permission is likely missing." >&2
  exit 5
fi

echo "Wrote $PNG"
file "$PNG"
echo "Native window capture complete (includes macOS title bar and traffic lights)."

if [[ "$KEEP" == "1" ]]; then
  echo "--keep set: leaving jvm-app running (pid $APP_PID). Harness home: $HARNESS_TMP_HOME"
  trap - EXIT
else
  echo "App log: $APP_LOG"
fi
