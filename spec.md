# postui-widget — Specification

**Status:** Draft v1 — Phase 1 (spike) in progress, see #1
**Platform:** Android only (see Platform Decision)
**Language:** Kotlin, no cross-platform framework
**First test case:** Note Dude NA artifact

---

## 1. Problem

Claude artifacts cannot be viewed on mobile home screens. Existing generic
solutions (e.g. AnyWidget) render a web page into a fixed widget roughly
one third of the screen, with no respect for the actual widget dimensions the
OS reports. The result is unreadable for content-dense pages.

The goal is a widget that displays an arbitrary web page — initially a published
Claude artifact — at or near full screen size, rendered at the correct
resolution for the widget instance the user actually placed.

Primary use case: swipe to a home screen page and read a task list without
tapping anything.

---

## 2. Goals

- G1. Display an arbitrary URL as a home screen widget.
- G2. Support widget sizes up to the largest the launcher grid allows
  (target 4×5 / 5×5 cells, i.e. near-full-screen).
- G3. Render the page at the widget's **actual** pixel dimensions, not a fixed
  resolution that is then scaled.
- G4. Support multiple widget instances, each with its own URL.
- G5. Refresh content automatically on a schedule, and on demand via tap.
- G6. Survive launcher restarts, device reboots, and widget resizes.

## 3. Non-Goals

- N1. iOS support. (Deferred — see Platform Decision.)
- N2. Interactivity inside the widget. Scrolling, link clicks, form input and
  text selection are impossible in an Android app widget. The widget is a
  picture.
- N3. Authenticated pages. v1 assumes a publicly reachable URL.
- N4. Any Claude-specific integration. The widget takes a URL; that the URL
  happens to be an artifact share link is incidental.
- N5. A full in-app browser.

---

## 4. Platform Decision

Two separate decisions. Only the first one affects how large the widget can be.

### 4a. Android over iOS — this is what decides the size ceiling

iOS caps home screen widgets at `systemLarge` (~half the screen on iPhone);
`systemExtraLarge` is iPad-only. The core goal — near-full-screen — is only
achievable on Android, where widgets resize freely up to the full launcher grid.

### 4b. Native Kotlin over React Native / Flutter — size is unaffected

Widget size on Android is determined solely by `res/xml/widget_info.xml` and the
launcher. That file and that launcher are identical whichever framework builds
the app, so choosing React Native or Flutter would **not** reduce the maximum
widget size.

Native was chosen because a framework would only ever own one screen:

1. Neither React Native nor Flutter can build Android widget UI — it is
   `RemoteViews`, inflated by the launcher (§5). They can only bridge data to a
   widget written natively.
2. The off-screen WebView renderer runs from a background worker with no UI.
   That is native code in every option.
3. Everything non-trivial — widget provider, renderer, bitmap pipeline,
   scheduling — is therefore Kotlin regardless. A framework would wrap the
   one-field `ConfigActivity` and add a JS/Dart bridge, a plugin such as
   `home_widget`, several MB of runtime, and friction launching the framework
   from the widget-configure intent.

The native surface is small (roughly six classes). If a richer companion app is
wanted later, a Flutter shell can wrap the same Kotlin pieces without loss.

---

## 5. Core Constraint (drives the whole architecture)

**An Android app widget cannot load a URL and cannot host a WebView.**

A widget's UI is a `RemoteViews` object: a serialized description of views that
the *launcher* process inflates. `RemoteViews` supports only a whitelist of
layout and view classes, and descendants of those classes are not supported.
`WebView` is not on the list. No code runs in the widget.

Therefore the app is not a passive shell. **The app is the renderer.** It owns
an off-screen `WebView`, draws it into a bitmap at the widget's dimensions, and
pushes that image into an `ImageView` in the widget.

---

## 6. Architecture

```
┌─────────────────────────────────────────────────────┐
│ App process                                         │
│                                                     │
│  ConfigActivity ──writes──▶ SharedPreferences       │
│   (URL per widget id)        (appWidgetId → config) │
│                                      │              │
│  RenderWorker (WorkManager)          │              │
│   ├─ read config ◀───────────────────┘              │
│   ├─ read widget size from AppWidgetManager options  │
│   ├─ off-screen WebView @ exact px                  │
│   ├─ measure → layout → draw(canvas) → Bitmap       │
│   ├─ write PNG to filesDir/widgets/<id>_<seq>.png   │
│   └─ RemoteViews.setImageViewUri(FileProvider uri)  │
│              │                                      │
└──────────────┼──────────────────────────────────────┘
               ▼
┌─────────────────────────────────────────────────────┐
│ Launcher process — inflates RemoteViews, shows image │
└─────────────────────────────────────────────────────┘
```

### 6.1 Components

| Component | Responsibility |
|---|---|
| `WidgetProvider : AppWidgetProvider` | Lifecycle hooks: `onUpdate`, `onAppWidgetOptionsChanged`, `onDeleted`. Enqueues render work. Does not render. |
| `res/xml/widget_info.xml` | `<appwidget-provider>` — declares sizes, resize behaviour, config activity, preview. |
| `ConfigActivity` | Launched on widget placement. Prompts for URL, persists it against the `appWidgetId`, returns `RESULT_OK` with the id. |
| `WidgetRenderer` | Off-screen WebView → bitmap. Pure rendering, no Android widget knowledge. |
| `WidgetImages` | Writes PNGs to `filesDir/widgets`, returns `FileProvider` URIs, grants launcher read access, deletes stale files. |
| `WidgetFiles` | Pure naming / stale-file rules for rendered PNGs (no Android types; JVM-tested). |
| `WidgetViews` | Builds the widget's `RemoteViews` (image or placeholder, tap intent). |
| `RenderWorker : CoroutineWorker` | Scheduling wrapper. Resolves size + config, calls renderer, pushes `RemoteViews`. |
| `WidgetStore` | Thin `SharedPreferences` wrapper keyed by `appWidgetId`. |
| `FileProvider` | Grants the launcher read access to rendered PNGs. |

### 6.2 Data model

`SharedPreferences`, one file, flat keys:

```
widget_<appWidgetId>_url          String
widget_<appWidgetId>_interval     Int (minutes, default 15)
widget_<appWidgetId>_last_render  Long (epoch ms)
widget_<appWidgetId>_seq          Int (cache-busting counter)
```

No database. If config grows past ~5 fields per widget, revisit.

---

## 7. Detailed Requirements

### 7.1 Sizing

- R1.1 On `onAppWidgetOptionsChanged` and on every render, read the options
  bundle via `AppWidgetManager.getAppWidgetOptions(appWidgetId)`.
- R1.2 Portrait dimensions are `OPTION_APPWIDGET_MIN_WIDTH` ×
  `OPTION_APPWIDGET_MAX_HEIGHT`. Landscape is `MAX_WIDTH` × `MIN_HEIGHT`.
  Values are in **dp** and must be converted to px via display density.
- R1.3 Render at the portrait dimensions for v1. (Landscape: open question Q3.)
- R1.4 A resize must trigger a re-render. Debounce — resize fires repeatedly
  during the drag.
- R1.5 `widget_info.xml` must declare:
  - `targetCellWidth` / `targetCellHeight` (API 31+) set to a large default
  - `minWidth` / `minHeight` — conservative floor
  - `minResizeWidth` / `minResizeHeight` — allow shrinking
  - `maxResizeWidth` / `maxResizeHeight` (API 31+) — set high enough to permit
    a near-full-screen drag
  - `resizeMode="horizontal|vertical"`
  - `configure` pointing at `ConfigActivity`
  - `updatePeriodMillis="0"` (scheduling is WorkManager's job)

### 7.2 Rendering

- R2.1 `WebView` must be created and driven on the main thread, even though the
  worker runs on a background thread. Post to the main looper and suspend until
  complete.
- R2.2 Instantiate off-screen (not attached to any window). Enable JavaScript
  and DOM storage.
- R2.3 Explicitly `measure()` with `EXACTLY` specs at the target px, then
  `layout(0, 0, w, h)`, then `draw(canvas)` into a `Bitmap`.
- R2.4 `onPageFinished` is not sufficient for a JS-rendered artifact. Wait for
  page finish **plus** a settle delay (default 750 ms, configurable) before
  drawing. Consider a JS readiness probe later (Q2).
- R2.5 Hard timeout of 20 s per render. On timeout, keep the previous image and
  log; never show a blank widget.
- R2.6 A main-frame load error (network failure, HTTP ≥ 400) fails the render;
  the previous image is kept.

### 7.3 Bitmap delivery — **critical**

- R3.1 **Do not use `setImageViewBitmap`.** `RemoteViews` crosses a Binder
  transaction with a practical ceiling around 1 MB. A near-full-screen
  `ARGB_8888` bitmap (e.g. 1080×2000) is ~8.6 MB and will throw
  `TransactionTooLargeException`. This is almost certainly why existing apps
  stay small and fixed.
- R3.2 Write the bitmap to a PNG in internal storage and deliver it with
  `setImageViewUri` and a `FileProvider` content URI. Grant read access on each
  URI to every installed home-screen app (`CATEGORY_HOME`), since the launcher
  process is the one that opens it.
- R3.3 The launcher caches by URI. Writing new bytes to the *same* URI may not
  refresh the display. Bust the cache by incrementing `seq` and using a new
  filename (or appending a changing query parameter) on every render.
- R3.4 Delete stale PNGs for that widget id after a successful swap (revoking
  their URI grants). Delete all of a widget's files in `onDeleted`.

### 7.4 Refresh

- R4.1 Scheduling via `WorkManager` periodic work, one unique work name per
  `appWidgetId`. Minimum interval is 15 minutes (OS-enforced).
  `updatePeriodMillis` is not used — its floor is 30 minutes.
- R4.2 Re-render immediately on: widget placement, config change, resize,
  `onUpdate`, and `ACTION_BOOT_COMPLETED`.
- R4.3 Tapping the widget triggers an immediate one-shot re-render **and**
  opens the URL. (Tap behaviour: see Q1.)
- R4.4 Doze will delay scheduled work. Accept it; do not request battery
  exemptions in v1.
- R4.5 WorkManager enables/disables one of its own components as its queue
  fills and empties. Each toggle is a package change: the launcher rebuilds the
  widget from `initialLayout` and the system sends `APPWIDGET_UPDATE`. So
  `onUpdate` must (a) always re-push the latest image (or the placeholder), and
  (b) skip re-rendering a widget rendered within the last 60 s, or every
  completed render would trigger another.

### 7.5 Error states

- R5.1 No network / load failure → keep the last successful image, overlay a
  small staleness indicator if the last render is older than 3× the interval.
- R5.2 No URL configured → render a placeholder prompting the user to tap.
- R5.3 Never render a blank or white widget. A rendered bitmap whose sampled
  pixels are all one colour is treated as a failed render.

---

## 8. Acceptance Criteria

The build is done when, on a physical Android device:

- A1. A widget can be placed, prompts for a URL, and renders that page.
- A2. The widget can be dragged to at least 4×5 cells and the content re-renders
  crisply at the new size — not a stretched version of the old bitmap.
- A3. Text in a near-full-screen widget is legible, i.e. rendered at native
  resolution, not upscaled.
- A4. Two widgets with two different URLs coexist and update independently.
- A5. Content refreshes unattended within ~20 minutes of a source change.
- A6. The widget survives a reboot and still refreshes.
- A7. Removing a widget leaves no orphaned files or scheduled work.
- A8. No `TransactionTooLargeException` at the maximum supported size.
- A9. The Note Dude NA artifact is readable at a glance with no interaction.

---

## 9. Phases

**Phase 1 — Spike.** Hardcoded URL, hardcoded size, manual refresh only.
Goal: prove the off-screen WebView → bitmap → FileProvider → `ImageView` path
works at large dimensions. This is the only genuinely risky part; everything
else is plumbing. Stop here and validate before continuing.

Phase 1 implementation notes:
- URL comes from the Gradle property `postui.spikeUrl` (baked into `BuildConfig`).
- Size is fixed at 1080×2000 px.
- Rendering already runs as a one-shot `RenderWorker`, because a render can take
  up to 20 s, longer than a broadcast receiver may run. Periodic scheduling is
  still Phase 4.
- Tap only refreshes (Q1 still open).

**Phase 2 — Real sizing.** Read the options bundle, render at actual
dimensions, handle resize. Declare large resize targets. Satisfies A2/A3.

**Phase 3 — Multi-instance + config.** `ConfigActivity`, per-id storage,
cleanup on delete. Satisfies A1/A4/A7.

**Phase 4 — Scheduling.** WorkManager, boot receiver, tap-to-refresh, error
states. Satisfies A5/A6.

**Phase 5 — Note Dude NA.** Build a widget-shaped layout for the artifact —
fixed height, no scroll, large type. Satisfies A9.

---

## 10. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Launcher ignores new image despite new URI | High | Cache-busting filenames (R3.3); test on multiple launchers |
| Off-screen (unattached) WebView draws nothing | High | Phase 1 instrumented tests assert real pixels from a software-canvas draw |
| OEM launchers cap widget size below the stock grid | Medium | Test on stock Android first; document per-launcher limits |
| Artifact doesn't render in WebView (CSP, auth, JS timing) | Medium | Phase 1 spike uses the real artifact URL, not a placeholder |
| Memory pressure from large bitmaps | Medium | Consider `RGB_565`; recycle aggressively; cap max dimension |
| WorkManager ↔ `onUpdate` feedback loop | Medium | R4.5 recent-render guard; periodic work in Phase 4 also keeps the queue non-empty |
| Doze delays refresh beyond usefulness | Low | Accept; tap-to-refresh is the escape hatch |

---

## 11. Open Questions

- Q1. Should tapping the widget open the artifact in a browser, open an in-app
  WebView, or only trigger a refresh? *(Phase 1: refresh only.)*
- Q2. Is a settle delay sufficient for artifact render timing, or is a JS
  readiness probe needed (e.g. poll for a sentinel element)?
- Q3. Render both orientations and supply `RemoteViews(landscape, portrait)`,
  or portrait only?
- Q4. Does the Note Dude NA artifact need a dedicated widget layout mode, or
  does its existing layout fit a 4×5 widget as-is?
- Q5. Minimum supported API level? API 31 unlocks `targetCellWidth` /
  `maxResizeWidth`, which materially improve large-size behaviour.
  *(Tentative: `minSdk 31`. Cheap to lower later.)*

---

## 12. Out of Scope for v1 (parked)

- iOS widget via WidgetKit + App Group image handoff
- Authenticated / session-bound URLs
- Pulling artifacts directly from a Claude account rather than share links
- Lock screen widgets
- A prompt input surface in the widget
