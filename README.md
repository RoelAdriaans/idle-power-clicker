# ⚡ Idle Power Helper

> *Why tap when you can automate?* 🤖✨

An Android overlay app that plays **[Idle Power](https://play.google.com/store/apps/details?id=com.deuskigames.idlepowerbeta)** for you. 🎮 It figures out the optimal merge sequence, and swipes for you — forever. 🔄 Just open the game, position the grid overlay, hit Start, and go touch grass. 🌿☀️

---

## 🔋 About Idle Power

**[Idle Power](https://play.google.com/store/apps/details?id=com.deuskigames.idlepowerbeta)** is a free-to-play idle/incremental game by **Deuski Games**, available on the Google Play Store. 🏪

> *"Your systems have come online. Your programming directs you to generate as much energy as possible, by any means necessary… The world, and indeed the universe, will find out just what happens when an artificial intelligence is given a single task: Maximize yourself at all costs."* 🤖

You play as an **AI** 🧠 tasked with maximizing energy output. Numbers go to infinity. 🔢♾️ It's deeply satisfying. The game runs **four main loops**:

| Section | What you do |
|---------|------------|
| ⚡ **PRODUCE** | Drag the sun ☀️ to power solar panels. Unlock more energy sources as you grow. |
| 💰 **SELL** | Sell energy to a power grid. Invest in infrastructure. Watch the numbers get silly big. |
| 🔋 **EXPAND** | Synthesize batteries and **merge them** on a 4×4 grid to create more powerful ones. |
| 🔄 **REBUILD** | Prestige — tear it all down, keep permanent upgrades, run it all again but faster. |

The game progresses **offline** 😴 while you're away. One player has been at it for **80+ days** and called it *"peak game."* 🏆

### 💬 What players say

> ⭐⭐⭐⭐⭐ *"Peak game. ADs are optional, not forced. Gameplay loop is fun, the buildings are unique. I've been playing for 80 days idle time and I am addicted."* — lennon hargreaves

> ⭐⭐⭐⭐⭐ *"Really like this game"* — and then they kept playing for another month 😂

[![Get it on Google Play](https://img.shields.io/badge/Google_Play-Get_Idle_Power-green?logo=google-play&style=for-the-badge)](https://play.google.com/store/apps/details?id=com.deuskigames.idlepowerbeta)

---

## 🎮 What Idle Power Helper Does

The battery merging is satisfying. 😌 It's also endless and repetitive. 😮‍💨 This app does the repetitive part so you can focus on the fun upgrades. 💸

Idle Power has a 4×4 grid of batteries. 🔋 You merge matching batteries to create higher-tier ones. It's satisfying. 😌 It's also repetitive. 😮‍💨 This app does the repetitive part.

- 🔲 Draws a transparent grid overlay on top of the game
- 👆 Injects real swipe gestures via the Android Accessibility API
- 🧠 Uses a mathematically proven merge sequence — zero wasted moves
- 📊 Shows live progress: move counter, moves left, ETA to finish
- ⏸️ Pause and resume without losing your place
- 🔍 Debug mode to visually calibrate the grid
- 😴 Works while you sleep

---

## 🧮 The Algorithm

This isn't random. 🎲❌ It's not a naive sweep. 🧹❌ It's a **Tower of Hanoi sequence** 🗼 mapped onto a snake path 🐍 through all 16 cells.

```
(3,3)→(3,2)→(3,1)→(3,0)→(2,0)→(2,1)→(2,2)→(2,3)
                                              ↓
(1,3)←(1,2)←(1,1)←(1,0)←────────────────────╯
  ↓
(0,0)→(0,1)→(0,2)→(0,3)  ← 🏆 target
```

Every merge in the sequence is **guaranteed valid** ✅ — same-tier merges only, in the exact right order. The target cell `(0,3)` climbs from tier A all the way to tier P 🎉 (16th tier). That takes exactly **32,767 moves** per cycle. Then it starts over. 🔄

Want the full breakdown with worked examples and timing tables? 📖 Check out [`ALGORITHM.md`](ALGORITHM.md). 🤓

---

## ⏱️ How Long Does One Cycle Take?

| Speed | Delay | Time per cycle |
|-------|-------|---------------|
| 🐢 Slow | 1.5s / move | ~13.7 hours 😴 |
| 🚶 Normal | 0.8s / move | ~7.3 hours ☕ |
| 🚀 Fast | 0.35s / move | ~3.2 hours 🔥 |

The first 8 tiers fly by in under a minute. ⚡ The last tier alone takes half the total time. 😅 Classic binary doubling. 📈🎓

---

## 🚀 Getting Started

### 📋 Prerequisites

- 📱 Android device running Android 8.0+ (API 26)
- 🛠️ Android Studio (for building)
- 🔌 USB debugging enabled on your device

### 🔨 Build & Install

```bash
# Build and install
./gradlew installDebug

# Re-enable the accessibility service after install 🔐
# (Android resets it on every app update — this does it for you via ADB)
./gradlew installDebug && ./gradlew enableA11y
```

### 🎬 First Run

1. 📱 Open the app — grant **Overlay** and **Screen Capture** permissions when asked
2. ♿ Enable **Idle Power Helper** in Android → Accessibility Settings
   *(or run `./gradlew enableA11y` after installing 🤙)*
3. 🎮 Open Idle Power
4. ⚡ Tap the **IPH** floating panel to bring up controls
5. 🔍 Tap **Debug** to show the grid overlay
6. 🎯 Use the **Move grid ↑↓←→** buttons to position the overlay over the battery grid
7. 💾 Tap **Save** to lock in the calibration
8. 🔍 Tap **Hide** to close the debug overlay
9. ▶️ Tap **Start** and watch the magic happen ✨🎉

---

## 🎛️ Controls

```
⚡ IPH
──────────────
▶ Start          ← tap to begin 🚀 / ⏸ Pause to stop without losing progress 💾

[ 😴 Idle screen ]
  🐇 Speed
  ○ Slow (1.5s)  🐢
  ● Normal (0.8s) 🚶
  ○ Fast (0.35s) 🚀
  🔍 Debug
  ↺ Reset         ← zero the move counter (only shown while paused/idle) 🔄
  ✕ Close

[ 🔥 Running screen ]
  ◀◀2x  ▶▶2x   ← 2x2 grid, tap one to change speed/direction (only shown while running) ⚡
  ▶▶4x           the active button turns green; tap it again for normal 1x forward
                       tap the active one again to go back to normal 1x forward
  1234 / 32767    ← current move 📍
  31533 left      ← almost there... 😤
  ETA 2h 47m      ← go make a coffee ☕ (updates live with speed/direction)
```

---

## 🔍 Grid Calibration

Getting the grid aligned is the only manual step. 🎯 Open debug mode and you'll see a magenta 🟣 grid drawn over the screen. The numbers in each cell show the row/col coordinates.

**🔀 Move grid buttons** (use these first):
- **↑↓** — shift the whole grid up or down (40px per tap)
- **←→** — shift the whole grid left or right

**📐 Edge buttons** (use these to resize):
- **Top / Bot** — move individual edges to resize vertically
- **Left / Right** — move individual edges to resize horizontally

When the grid lines up with the battery cells, hit 💾 Save. The calibration persists across restarts. 🙌

> 💡 **Tip:** If swipes land one row off 😤, use the Move grid ↓ button a few times until they land correctly. 🎯

---

## 🐛 Debugging

All moves are logged to Logcat 📋 with the tag `IPH`:

```
# In Android Studio Logcat 🔬, filter by:
tag: IPH

# Each line looks like:
SW[1234]:(0,2)→(0,3) 661,1193→904,1193
#  ↑ move#  ↑ cells      ↑ pixel coordinates
```

---

## 🏗️ Architecture

```
MainActivity 🏠
    └─▶ OverlayService 🧠 (foreground service)
            ├── BatteryMerger 🔢    — pure algorithm, no Android deps
            ├── GridConfig 🗺️       — calibration persistence
            ├── GridDebugView 🎨    — transparent overlay for calibration
            └── SwipeAccessibilityService 👆
                    └── dispatchGesture() — injects real touch events
```

The merge loop runs on `Dispatchers.Default` 🧵. All UI updates hop back to the main thread 🔄. The accessibility service lives separately and gets called via a static instance reference 📡. It's beautifully janky and it works. 😌✨

---

## 📁 Project Structure

```
app/src/main/
├── java/com/example/idlepowerhelper/
│   ├── MainActivity.kt              🏠 entry point
│   ├── OverlayService.kt            🧠 the brain
│   ├── BatteryMerger.kt             🔢 the algorithm
│   ├── GridConfig.kt                🗺️ calibration storage
│   ├── GridDebugView.kt             🎨 the magic grid drawing
│   └── SwipeAccessibilityService.kt 👆 touch injector
└── res/layout/
    └── overlay_panel.xml            🎛️ the floating UI
```

---

## 🤝 Built With Love And Laziness

- **Kotlin** 💜 — because Java is too much typing 😅
- **Android Accessibility API** ♿ — for gesture injection without root 🔓
- **Coroutines** ⚡ — for the async merge loop
- **WindowManager** 🪟 — for drawing overlays on top of other apps
- **Tower of Hanoi math** 🗼🧮 — who knew a classic CS puzzle would end up in an idle game bot 🤯

---

## ⚠️ Disclaimer

This app uses the Android Accessibility API to inject gestures. 🤖 Use it at your own risk. 🎲 Don't blame us if your phone turns into a perpetual motion battery-merging machine. 🔋♾️🌀

> 📝 **Note:** The game's package ID is `com.deuskigames.idlepowerbeta` — it launched as a beta and the name stuck. 🐣

---

📲 **[Download Idle Power on Google Play](https://play.google.com/store/apps/details?id=com.deuskigames.idlepowerbeta)** — go play it, then come back here and automate it. 😎

---

*Vibe coded with ☕ and a healthy disregard for doing things manually.* 😎🚀


