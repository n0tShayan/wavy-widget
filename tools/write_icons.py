"""Writes the small static resources (icons, shapes, values). Run once; output is checked in."""
import os

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
X = '<?xml version="1.0" encoding="utf-8"?>\n'
A = 'xmlns:android="http://schemas.android.com/apk/res/android"'

ICONS = {
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
    "values/styles.xml": """<resources>
    <style name="WidgetTitle">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:ellipsize">end</item>
        <item name="android:maxLines">1</item>
        <item name="android:textColor">#FFFFFFFF</item>
        <item name="android:shadowColor">#80000000</item>
        <item name="android:shadowRadius">8</item>
        <item name="android:textSize">18sp</item>
        <item name="android:textStyle">bold</item>
    </style>

    <style name="WidgetArtist" parent="WidgetTitle">
        <item name="android:layout_marginTop">1dp</item>
        <item name="android:textColor">#B3FFFFFF</item>
        <item name="android:textSize">13sp</item>
        <item name="android:textStyle">normal</item>
    </style>

    <style name="WidgetTime">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:textColor">#99FFFFFF</item>
        <item name="android:textSize">11sp</item>
    </style>

    <!-- Horizontal style: stretches the drawable to the view (no aspect fit) and brings no tint. -->
    <style name="WidgetWave" parent="@android:style/Widget.ProgressBar.Horizontal">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">16dp</item>
        <item name="android:minHeight">16dp</item>
        <item name="android:maxHeight">16dp</item>
        <item name="android:indeterminate">true</item>
        <item name="android:importantForAccessibility">no</item>
    </style>

    <style name="WidgetMorph" parent="WidgetWave">
        <item name="android:layout_width">28dp</item>
        <item name="android:layout_height">28dp</item>
        <item name="android:layout_gravity">center</item>
        <item name="android:minHeight">28dp</item>
        <item name="android:maxHeight">28dp</item>
        <item name="android:visibility">gone</item>
    </style>
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
    "drawable/seek_cover.xml": X + """<!-- Hides the not-yet-played wave. Level = 10000 - progress, so the scaled layer's left
     edge is the progress head (inset 2dp so the handle never clips at either end).
     Draws the unplayed track: a gap for the handle, a rounded line and an end dot. -->
<inset %s
    android:insetLeft="2dp" android:insetRight="2dp">
    <scale android:scaleGravity="right" android:scaleWidth="100%%">
        <layer-list>
            <item android:left="-5dp" android:right="-2dp">
                <shape><solid android:color="@color/panel" /></shape>
            </item>
            <item android:left="5dp" android:height="3.5dp" android:gravity="center_vertical|fill_horizontal">
                <shape><solid android:color="#2EFFFFFF" /><corners android:radius="1.75dp" /></shape>
            </item>
            <item android:right="-1.75dp" android:width="3.5dp" android:height="3.5dp"
                android:gravity="right|center_vertical">
                <shape android:shape="oval"><solid android:color="#80FFFFFF" /></shape>
            </item>
        </layer-list>
    </scale>
</inset>
""" % A,
    "drawable/seek_thumb.xml": X + """<!-- Progress handle, positioned like seek_cover and tinted with the album colour. -->
<inset %s
    android:insetLeft="2dp" android:insetRight="2dp">
    <scale android:scaleGravity="right" android:scaleWidth="100%%">
        <layer-list>
            <item android:left="-2dp" android:width="4dp" android:height="16dp"
                android:gravity="left|center_vertical">
                <shape><solid android:color="#FFFFFFFF" /><corners android:radius="2dp" /></shape>
            </item>
        </layer-list>
    </scale>
</inset>
""" % A,
    "drawable/seek_fade.xml": X + """<!-- Softens the wave's start so it emerges from the panel instead of being cut. -->
<shape %s>
    <gradient android:angle="0" android:startColor="@color/panel" android:endColor="#00101012" />
</shape>
""" % A,
    "animator/press_scale.xml": X + """<!-- Buttons dip on press and spring back on release. Runs in the launcher, only on touch. -->
<selector %s>
    <item android:state_pressed="true">
        <set>
            <objectAnimator android:propertyName="scaleX" android:valueTo="0.88" android:valueType="floatType"
                android:duration="120" android:interpolator="@android:interpolator/fast_out_slow_in" />
            <objectAnimator android:propertyName="scaleY" android:valueTo="0.88" android:valueType="floatType"
                android:duration="120" android:interpolator="@android:interpolator/fast_out_slow_in" />
        </set>
    </item>
    <item>
        <set>
            <objectAnimator android:propertyName="scaleX" android:valueTo="1" android:valueType="floatType"
                android:duration="380" android:interpolator="@android:anim/overshoot_interpolator" />
            <objectAnimator android:propertyName="scaleY" android:valueTo="1" android:valueType="floatType"
                android:duration="380" android:interpolator="@android:anim/overshoot_interpolator" />
        </set>
    </item>
</selector>
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
