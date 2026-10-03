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
| Wave animation, grow on play, coast and flatten on pause | Launcher's RenderThread (AnimatedVectorDrawable) | Zero; stops when home isn't visible |
| Play / pause icon morph | Launcher's RenderThread (AnimatedVectorDrawable) | Zero, plus one tiny update to swap in the static icon afterwards |
| Art, title and colour crossfade on track change | Launcher (`animateLayoutChanges`) | Zero, plus one tiny update to free the old art afterwards |
| Tap-to-seek on the wave line | 24 cached tap zones; one `seekTo` and one tiny update per tap | Only when tapped |
| Like / unlike (heart, top right) | Player's own like action, read from the cached playback state; pop runs on the RenderThread | Only when tapped or when the like changes |
| Button press dip and spring | Launcher (`stateListAnimator`) | Zero; runs only on touch |
| Elapsed time | Launcher's `Chronometer` | Zero |
| Progress bar and handle | One ~100-byte partial update, at most once a second and only when it moves a pixel | Only while playing, screen on and unlocked |
| Album colours and background | Rendered once per track | A few ms per song |
| Track, play and pause changes | MediaSession callbacks, cached | Event driven, no polling, no repeat bitmap transfers |

There's no foreground service, no wakelock, no alarms and no network access. The process is
kept alive by the system's own notification-listener binding.

Metadata and playback state are cached from the session callbacks. Reading them back from
the player is a binder call, and the metadata carries the full album bitmap. Repeated
metadata callbacks that change nothing on screen don't re-send any bitmaps.

Android 11 widgets can't tint an animated progress bar. So the wave is a panel-coloured
*mask* with a sine-shaped hole, animated by the launcher, sitting on an album-coloured
gradient. A level-scaled cover hides the unplayed part. Its left edge is the progress head,
so it also draws the gap, the rounded track and the end dot. A handle on the same level,
tinted with the gradient colour at that point, sits on top. That's why the control panel
has a fixed dark colour (`#101012`).

Animations that RemoteViews can't trigger directly are started by visibility. An
indeterminate `ProgressBar` starts its AnimatedVectorDrawable when it becomes visible. A
parent with `animateLayoutChanges` fades its children in and out, so the art, text and
colours each have an `a` and a `b` copy and the app flips which one is visible.

Home screen widgets only receive taps, never drags, so seeking is tap-to-seek: tap anywhere
on the wave line to jump there. The heart appears when the playing app offers a like
control (Spotify's "Liked Songs"). If it doesn't show, open the app: the setup screen lists
the controls the player exposes.

## Build

```
set JAVA_HOME=%USERPROFILE%\android-build\jdk\jdk-17.0.20.1+1
gradlew assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`.

To change the wave (wavelength, amplitude, thickness, speed, grow and shrink timing) or the
play / pause shapes, edit `tools/gen_wave.py` and run it. Keep `SETTLE_MS` in
`WidgetController` longer than its longest animation. Static drawables, styles and the
seek bar cover and handle come from `tools/write_icons.py`.
