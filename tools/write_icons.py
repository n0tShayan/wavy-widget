"""Writes the small static resources (icons, shapes, values). Run once; output is checked in."""
import os

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
X = '<?xml version="1.0" encoding="utf-8"?>\n'
A = 'xmlns:android="http://schemas.android.com/apk/res/android"'

ICONS = {
    "ic_play": ("#FF101012", "M8,6.82v10.36c0,0.79 0.87,1.27 1.54,0.84l8.14,-5.18c0.62,-0.39 0.62,-1.29 0,-1.69L9.54,5.98C8.87,5.55 8,6.03 8,6.82z"),
    "ic_pause": ("#FF101012", "M8,19c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2v10c0,1.1 0.9,2 2,2zM14,7v10c0,1.1 0.9,2 2,2s2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2z"),
    "ic_next": ("#FFFFFFFF", "M7.58,16.89l5.77,-4.07c0.56,-0.4 0.56,-1.24 0,-1.63L7.58,7.11C6.91,6.65 6,7.12 6,7.93v8.14c0,0.81 0.91,1.28 1.58,0.82zM16,7v10c0,0.55 0.45,1 1,1s1,-0.45 1,-1V7c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1z"),
    "ic_prev": ("#FFFFFFFF", "M7,6c0.55,0 1,0.45 1,1v10c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V7c0,-0.55 0.45,-1 1,-1zM10.66,12.82l5.77,4.07c0.66,0.47 1.58,-0.01 1.58,-0.82V7.93c0,-0.81 -0.91,-1.28 -1.58,-0.82l-5.77,4.07c-0.57,0.4 -0.57,1.24 0,1.64z"),
}

FILES = {
    "values/strings.xml": """<resources>
    <string name="app_name">Media Widget</string>
    <string name="widget_name">Now playing</string>
    <string name="not_playing">Not playing</string>
    <string name="tap_to_open">Tap to open Spotify</string>
    <string name="setup_title">Finish setup</string>
    <string name="setup_sub">Tap to allow notification access</string>
</resources>
""",
    "values/colors.xml": """<resources>
    <!-- Must match PANEL in tools/gen_wave.py and WidgetRenderer.PANEL. -->
    <color name="panel">#FF101012</color>
</resources>
""",
    "xml/media_widget_info.xml": X + """<appwidget-provider %s
    android:minWidth="250dp"
    android:minHeight="180dp"
    android:minResizeWidth="250dp"
    android:minResizeHeight="140dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_media"
    android:previewImage="@drawable/widget_preview"
    android:widgetCategory="home_screen" />
""" % A,
    "animator/wave_phase.xml": X + """<!-- Shifts the wave by exactly one wavelength, so the loop is seamless. -->
<objectAnimator %s
    android:propertyName="translateX"
    android:valueFrom="0"
    android:valueTo="-28"
    android:valueType="floatType"
    android:duration="1100"
    android:repeatCount="infinite"
    android:repeatMode="restart"
    android:interpolator="@android:anim/linear_interpolator" />
""" % A,
    "drawable/seek_wave_anim.xml": X + """<animated-vector %s
    android:drawable="@drawable/seek_wave_mask">
    <target android:name="phase" android:animation="@animator/wave_phase" />
</animated-vector>
""" % A,
    "drawable/seek_cover.xml": X + """<!-- Hides the not-yet-played part of the wave. Level = 10000 - progress. -->
<clip %s
    android:drawable="@drawable/seek_cover_vec"
    android:clipOrientation="horizontal"
    android:gravity="right" />
""" % A,
    "drawable/panel_bg.xml": X + """<shape %s>
    <solid android:color="@color/panel" />
    <corners android:bottomLeftRadius="26dp" android:bottomRightRadius="26dp"
        android:topLeftRadius="0dp" android:topRightRadius="0dp" />
</shape>
""" % A,
    "drawable/circle_soft.xml": X + """<shape %s android:shape="oval">
    <solid android:color="#26FFFFFF" />
</shape>
""" % A,
    "drawable/circle_solid.xml": X + """<shape %s android:shape="oval">
    <solid android:color="#FFFFFFFF" />
</shape>
""" % A,
    "drawable/ripple_circle.xml": X + """<ripple %s android:color="#40FFFFFF">
    <item android:id="@android:id/mask">
        <shape android:shape="oval"><solid android:color="#FFFFFFFF" /></shape>
    </item>
</ripple>
""" % A,
    "drawable/ic_launcher_fg.xml": X + """<vector %s
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:strokeColor="#FF1ED760" android:strokeWidth="5" android:strokeLineCap="round"
        android:pathData="M30,54 C34,44 38,44 42,54 S50,64 54,54 S62,44 66,54 S74,64 78,54" />
</vector>
""" % A,
    "drawable/widget_preview.xml": X + """<layer-list %s>
    <item><shape><solid android:color="@color/panel" /><corners android:radius="26dp" /></shape></item>
    <item android:drawable="@drawable/ic_launcher_fg" android:gravity="center" />
</layer-list>
""" % A,
    "mipmap-anydpi-v26/ic_launcher.xml": X + """<adaptive-icon %s>
    <background android:drawable="@color/panel" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
""" % A,
}

for name, (color, path) in ICONS.items():
    FILES["drawable/%s.xml" % name] = X + """<vector %s
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="%s" android:pathData="%s" />
</vector>
""" % (A, color, path)

for rel, body in FILES.items():
    p = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", newline="\n") as fh:
        fh.write(body)
print(len(FILES), "files")
