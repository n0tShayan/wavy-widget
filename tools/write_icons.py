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
    <string name="like">Add to Liked Songs</string>
    <string name="unlike">Remove from Liked Songs</string>
</resources>
""",
    "values/colors.xml": """<resources>
    <!-- Must match PANEL in tools/gen_wave.py and WidgetRenderer.PANEL. -->
    <color name="panel">#FF101012</color>
    <!-- Liked Songs heart. -->
    <color name="liked">#FF1ED760</color>
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

    <!-- One tap-to-seek zone; 24 of these split the seek bar evenly. -->
    <style name="SeekZone">
        <item name="android:layout_width">0dp</item>
        <item name="android:layout_height">match_parent</item>
        <item name="android:layout_weight">1</item>
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
    "drawable/circle_dim.xml": X + """<shape %s android:shape="oval">
    <solid android:color="#4D000000" />
</shape>
""" % A,
    "drawable/ic_heart.xml": X + """<vector %s
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5 22,5.42 19.58,3 16.5,3zM12.1,18.55l-0.1,0.1 -0.1,-0.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,0.99 3.57,2.36h1.87C13.46,5.99 14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z" />
</vector>
""" % A,
    "drawable/ic_heart_filled.xml": X + """<vector %s
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@color/liked" android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z" />
</vector>
""" % A,
    "drawable/heart_pop_vec.xml": X + """<!-- 36 units across so the ring can burst past the 24-unit heart. -->
<vector %s
    android:width="36dp" android:height="36dp" android:viewportWidth="36" android:viewportHeight="36">
    <group android:name="ring" android:pivotX="18" android:pivotY="18" android:scaleX="0.4" android:scaleY="0.4">
        <path android:name="ring_path" android:strokeColor="@color/liked" android:strokeWidth="1.6"
            android:strokeAlpha="0" android:pathData="M18,6 A12,12 0 1,1 17.99,6 Z" />
    </group>
    <group android:name="pop" android:pivotX="18" android:pivotY="18" android:scaleX="0" android:scaleY="0">
        <group android:translateX="6" android:translateY="6">
            <path android:fillColor="@color/liked" android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z" />
        </group>
    </group>
</vector>
""" % A,
    "drawable/avd_heart_pop.xml": X + """<animated-vector %s android:drawable="@drawable/heart_pop_vec">
    <target android:name="pop" android:animation="@animator/heart_pop" />
    <target android:name="ring" android:animation="@animator/heart_ring" />
    <target android:name="ring_path" android:animation="@animator/heart_ring_fade" />
</animated-vector>
""" % A,
    "animator/heart_pop.xml": X + """<!-- Heart springs in past full size and settles. -->
<set %s>
    <objectAnimator android:propertyName="scaleX" android:valueFrom="0" android:valueTo="1" android:valueType="floatType"
        android:duration="420" android:interpolator="@android:anim/overshoot_interpolator" />
    <objectAnimator android:propertyName="scaleY" android:valueFrom="0" android:valueTo="1" android:valueType="floatType"
        android:duration="420" android:interpolator="@android:anim/overshoot_interpolator" />
</set>
""" % A,
    "animator/heart_ring.xml": X + """<set %s>
    <objectAnimator android:propertyName="scaleX" android:valueFrom="0.4" android:valueTo="1.4" android:valueType="floatType"
        android:duration="460" android:interpolator="@android:anim/decelerate_interpolator" />
    <objectAnimator android:propertyName="scaleY" android:valueFrom="0.4" android:valueTo="1.4" android:valueType="floatType"
        android:duration="460" android:interpolator="@android:anim/decelerate_interpolator" />
</set>
""" % A,
    "animator/heart_ring_fade.xml": X + """<objectAnimator %s android:propertyName="strokeAlpha"
    android:valueFrom="0.9" android:valueTo="0" android:valueType="floatType"
    android:duration="460" android:interpolator="@android:anim/decelerate_interpolator" />
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
