# postui-widget

An Android home-screen widget that shows any web page — initially a published
Claude artifact — at near-full-screen size, rendered at the widget's real
resolution. See [spec.md](spec.md).

## How it works

A widget can't host a WebView, so the app renders the page itself: an off-screen
`WebView` draws into a bitmap, the bitmap is saved as a PNG, and the widget's
`ImageView` loads it through a `FileProvider` URI.

## Developing without Android Studio

Everything runs from the terminal. You need JDK 17 and the Android SDK
(`ANDROID_HOME` set); the Gradle wrapper downloads everything else.

```sh
./gradlew test                       # JVM unit tests (fast, no device)
./gradlew assembleDebug              # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # install on the connected device/emulator
./gradlew connectedDebugAndroidTest  # on-device tests (note: uninstalls the app afterwards,
                                     # which removes any placed widgets)
```

Render a different page (Phase 1 uses one hardcoded URL):

```sh
./gradlew installDebug -Ppostui.spikeUrl=https://example.org
```

Emulator, headless (no window):

```sh
emulator -list-avds
emulator -avd <name> -no-window -no-audio -no-boot-anim &
adb wait-for-device
```

Useful while debugging:

```sh
adb logcat -s RenderWorker                                   # render results
adb shell run-as com.appsciences.postui ls files/widgets     # rendered PNGs
adb exec-out screencap -p > screen.png                       # screenshot
```

On a physical phone: enable Developer options → USB debugging, plug in, and
`adb devices` should list it.

## Layout

```
app/src/main/java/com/appsciences/postui/
  WidgetProvider.kt   widget lifecycle; enqueues renders
  RenderWorker.kt     WorkManager job: render → PNG → push to launcher
  WidgetRenderer.kt   off-screen WebView → Bitmap
  WidgetImages.kt     PNG files, FileProvider URIs, launcher grants
  WidgetViews.kt      RemoteViews (image / placeholder)
  WidgetStore.kt      per-widget SharedPreferences
  WidgetFiles.kt      file naming rules (pure Kotlin)
  BlankCheck.kt       blank-render detection (pure Kotlin)
app/src/main/res/xml/widget_info.xml   widget sizes and resize limits
```
