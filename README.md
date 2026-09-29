# Media Widget

A One UI 9 style Spotify home screen widget for Android 11 (built for a Galaxy Note 10 5G
on One UI 3.1). Album art fills the top with the track info overlaid, and an animated wave
seek bar takes its colours from the cover. Previous, play/pause and next are circular buttons.

## Install

1. Copy `MediaWidget.apk` to the phone and open it (allow "Install unknown apps" if asked).
2. Open **Media Widget** and grant **notification access**. That's how Android lets an app
   see what Spotify is playing.
3. Tap **Allow** under "Keep it awake" so One UI's sleeping apps feature doesn't kill it.
4. Tap **Add to home screen**, or long-press the home screen → Widgets → Media Widget.
   Resize to **4x3 or 4x4** for the full album-art look. The minimum size is 4x2.

## How it stays light

| Part | Who does the work | Cost to this app |
| --- | --- | --- |
| Wave animation | Launcher's RenderThread (AnimatedVectorDrawable) | Zero; stops when home isn't visible |
| Elapsed time | Launcher's `Chronometer` | Zero |
| Progress bar | One ~100-byte partial update per second | Only while playing, screen on and unlocked |
| Album colours and background | Rendered once per track | A few ms per song |
| Track, play and pause changes | MediaSession callbacks | Event driven, no polling |

There's no foreground service, no wakelock, no alarms and no network access. The process is
kept alive by the system's own notification-listener binding.

Android 11 widgets can't tint an animated progress bar. So the wave is a panel-coloured
*mask* with a sine-shaped hole, animated by the launcher, sitting on an album-coloured
gradient. A clip drawable covers the unplayed part. That's why the control panel has a fixed
dark colour (`#101012`).

## Build

```
set JAVA_HOME=%USERPROFILE%\android-build\jdk\jdk-17.0.20.1+1
gradlew assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`.

To change the wave shape (wavelength, amplitude, thickness), edit `tools/gen_wave.py`, run
it, and keep `valueTo` in `res/animator/wave_phase.xml` equal to `-WAVELEN`.
