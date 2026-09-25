# Copilot Instructions — Idle Power Helper

Android overlay app (Kotlin) that auto-merges batteries in the **Idle Power** idle game by
injecting swipe gestures via the Accessibility API.

---

## Build & Install

```bash
# Debug build
./gradlew assembleDebug

# Build + install on connected device
./gradlew installDebug

# Re-enable accessibility service after install (Android security resets it on each update)
./gradlew installDebug && ./gradlew enableA11y
```

There are **no unit tests** in this project. Validate changes with `assembleDebug`.

The `enableA11y` Gradle task (in `app/build.gradle.kts`) shells out to `adb` to flip the
`enabled_accessibility_services` secure setting — it requires a connected/authorized device
and does nothing on CI.

---

## ⚠️ `docs/ARCHITECTURE.md` describes a different, unimplemented algorithm

`docs/ARCHITECTURE.md` documents a computer-vision approach — `analyzeGrid()` /
`findBestMerge()` / HSV `bucketColor()` — that captures the screen via `MediaProjection` and
picks merges by reading battery colors live. **None of those functions exist in the current
source** (`grep` confirms it). `OverlayService` does set up `MediaProjection` /
`ImageReader` / `VirtualDisplay` (`setupCapture()`, `captureScreen()`), and `MainActivity`
still requests the screen-capture permission from the user, but `captureScreen()` is dead
code — nothing in the merge loop calls it. The **only** algorithm actually driving moves is
the deterministic `BatteryMerger.sweepMove()` sequence described below. Treat
`docs/ARCHITECTURE.md` as aspirational/stale; trust the source (`OverlayService.executeMergeStep()`)
over that doc when they disagree.

---

## Architecture

```
MainActivity  ──launches──▶  OverlayService          (foreground service, Dispatchers.Default)
                                    │
                                    ├── BatteryMerger          (pure Kotlin object, no Android deps)
                                    ├── GridConfig / GridBounds (SharedPreferences wrapper)
                                    ├── GridDebugView          (fullscreen transparent calibration overlay)
                                    └── SwipeAccessibilityService.instance
                                                │
                                        dispatchGesture() — absolute screen coordinates
```

### `OverlayService`
Central coordinator. Owns the floating `overlay_panel` control panel and the coroutine merge loop.
- Loop runs on `Dispatchers.Default`; all UI updates post to main thread via `Handler(Looper.getMainLooper()).post { }`.
- Uses `SupervisorJob` so individual step failures don't cancel the whole loop.
- `sweepIter` tracks position in the 32 767-move sequence and is **never reset on pause** — only on service restart, or explicitly via the **↺ Reset** button (`resetMoveCounter()`, only reachable while paused/idle).

### `SwipeAccessibilityService`
Exposes itself via `companion object { var instance }`. `performSwipe()` **must be called from the main thread** — it wraps `dispatchGesture()` which is main-thread-only.

### `BatteryMerger`
Stateless `object`. Generates a deterministic sequence of 32 767 valid same-tier merge moves
using a **Tower of Hanoi algorithm over a snake path through all 16 cells**. See `ALGORITHM.md`
for a full explanation. No Android dependencies.

### `GridBounds`
Stores **absolute pixel coordinates** (never percentages or dp). Persisted to SharedPreferences
via `GridConfig`. `cellCenter(row, col)` returns the centre pixel of a grid cell.

### `MainActivity`
Setup screen only — not shown while the overlay is active. Walks the user through three
gates before enabling **Launch**: overlay permission (`Settings.canDrawOverlays`),
accessibility service enabled (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`), and grid
calibration via percentage sliders (`GridConfig.save`). Launch requests the
`MediaProjectionManager` screen-capture intent and forwards the `resultCode`/`Intent` to
`OverlayService` via `startForegroundService()`, then finishes itself — the floating panel
takes over from there.

---

## The Merge Algorithm

The game rule: drag source S onto target T — if both are the same tier, T advances one tier and
S resets to tier A. If tiers differ, nothing happens.

The algorithm uses a **snake path** through all 16 cells:

```
(3,3)→(3,2)→(3,1)→(3,0)→(2,0)→(2,1)→(2,2)→(2,3)
                                              ↓
(1,3)←(1,2)←(1,1)←(1,0)←────────────────────╯
  ↓
(0,0)→(0,1)→(0,2)→(0,3)  ← target (reaches tier 16)
```

Each cell's source is the previous cell in the path. The recursion (`tryAdvance` / `buildTo`)
mirrors binary counting: to advance a cell one tier, first build its source to the same tier,
then merge. This guarantees **zero invalid merges** and produces exactly **2¹⁵ − 1 = 32 767
moves** per cycle. See `ALGORITHM.md` for worked examples and timing tables.

---

## UI Layout (`overlay_panel.xml`)

The floating panel has two visibility states:

**Idle** (`layout_idle` visible, `layout_running` gone):
- Speed selector (`rg_speed`): Slow 1.5s / Normal 0.8s / Fast 0.35s
- Status text (`tv_status`)
- Debug toggle button (`btn_debug`)
- Reset button (`btn_reset`): zeroes `sweepIter` back to 0. Lives inside `layout_idle`, so it's
  hidden automatically whenever the merge loop is running — no separate visibility logic needed.
- Close button (`btn_close`)

**Running** (`layout_running` visible, `layout_idle` + `btn_close` gone):
- Move counter (`tv_move_number`): e.g. `1234 / 32767`
- Moves left (`tv_moves_left`)
- ETA (`tv_eta`): time to complete current cycle at current speed

Debug mode (`layout_grid_adjust`) is only visible when `btn_debug` is active and shows
Move grid (↑↓←→), individual edge nudge controls, and a 💾 Save button.

---

## Grid Calibration

The debug overlay (`GridDebugView`) is a fullscreen transparent `TYPE_APPLICATION_OVERLAY`
window drawn with `FLAG_LAYOUT_IN_SCREEN`. It draws the calibrated 4×4 grid and highlights
the current swipe move.

Calibration uses nudge buttons (40 px per tap):
- **Move grid** (↑↓←→): shifts all 4 edges together — use this to fix a Y/X offset.
- **Grid edges** (Top/Bot/Left/Right ↑↓): resize individual edges.

After nudging, press 💾 Save to persist to SharedPreferences.

**Coordinate offset:** swipe gestures use absolute screen coordinates via `dispatchGesture()`,
while `GridBounds` are stored in the overlay's local coordinate space. `OverlayService` measures
a `gestureYOffset` at service startup (`measureGestureOffset()` — a throwaway 1×1 probe view
added at `(0,0)`, whose `getLocationOnScreen()` reveals how far the window origin sits below the
true screen top on this device) and adds it to every gesture Y coordinate. This offset is
re-measured (redundantly, but harmlessly) whenever the debug overlay is shown. Do not remove the
startup measurement — without it, `gestureYOffset` stays `0` until the debug overlay is opened at
least once, so swipes silently miss their targets on a fresh launch.

---

## Debugging

```
# View live move log in Android Studio Logcat
tag: IPH

# Each move is logged as:
# SW[1234]:(0,2)→(0,3) 661,1193→904,1193
#   └─ move index    └─ cell coords   └─ pixel coords
```

The app also logs `gestureYOffset` (both at startup and whenever the debug overlay is shown) and screen dimensions.

---

## Key Conventions

- **No Jetpack Compose** — all layouts are XML in `res/layout/`.
- **ViewBinding** is enabled for Activities; services use `findViewById` (no binding in services).
- `GridBounds` coordinates are **always raw pixels**, never dp or percentages.
- All UI mutations from coroutines must use `Handler(Looper.getMainLooper()).post { }`.
- `foregroundServiceType="mediaProjection"` in the manifest is required on Android 14+ — do not remove it.
- `minSdk = 26`, `compileSdk / targetSdk = 34`, Java/Kotlin target = 1.8 (set via `compileOptions` in `app/build.gradle.kts`).
- **AGP 9.x built-in Kotlin support**: there is no `org.jetbrains.kotlin.android` plugin and no `kotlinOptions {}` block — AGP applies its own bundled Kotlin compiler. Don't re-add either; it will fail to apply under AGP 9+.
- Gradle wrapper tracks the latest stable Gradle release; keep root `build.gradle.kts`'s AGP/Kotlin `classpath` versions and `settings.gradle.kts`'s `foojay-resolver-convention` version compatible with it (check each tool's own release notes before bumping the wrapper alone — AGP 8.x cannot run on Gradle ≥ 9.6, so wrapper and AGP versions must be upgraded together).
- Speed delays live in `SPEED_DELAY_MS` in `OverlayService.Companion` — one place to change them.
- `nudgeStep = 40` px per button tap in `OverlayService`.

---

## Files of Interest

| File | Purpose |
|------|---------|
| `OverlayService.kt` | Main coordinator: merge loop, overlay panel, gesture dispatch |
| `BatteryMerger.kt` | Pure algorithm: snake-path Tower of Hanoi sequence generator |
| `GridConfig.kt` | `GridBounds` data class + SharedPreferences persistence |
| `GridDebugView.kt` | Fullscreen calibration overlay with grid drawing and move arrow |
| `SwipeAccessibilityService.kt` | Gesture injection via `dispatchGesture()` |
| `overlay_panel.xml` | Floating control panel layout |
| `ALGORITHM.md` | Full algorithm documentation with examples and timing tables |
| `app/build.gradle.kts` | Contains `enableA11y` Gradle task for ADB accessibility re-enable |
