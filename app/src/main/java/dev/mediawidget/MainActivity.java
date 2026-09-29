package dev.mediawidget;

import android.app.Activity;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One-time setup screen. Not needed after the widget is on the home screen. */
public class MainActivity extends Activity {
    private TextView accessStatus;
    private Button accessButton;
    private TextView batteryStatus;
    private Button batteryButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = text("Media Widget", 26);
        root.addView(title);
        root.addView(text("A One UI 9 style Spotify widget for your home screen.", 15));

        root.addView(heading("1. Notification access"));
        root.addView(text("Needed to read what's playing. Nothing is stored or sent anywhere.", 14));
        accessStatus = text("", 14);
        root.addView(accessStatus);
        accessButton = button("Grant access", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(accessButton);

        root.addView(heading("2. Keep it awake (recommended)"));
        root.addView(text("Stops One UI from putting the widget to sleep. Its idle cost is near zero.", 14));
        batteryStatus = text("", 14);
        root.addView(batteryStatus);
        batteryButton = button("Allow", v -> startActivity(
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));
        root.addView(batteryButton);

        root.addView(heading("3. Add the widget"));
        root.addView(text("Or: long-press the home screen, then Widgets, then Media Widget. "
                + "Resize it to 4x3 or 4x4 for the full album-art look.", 14));
        root.addView(button("Add to home screen", v -> AppWidgetManager.getInstance(this)
                .requestPinAppWidget(new ComponentName(this, MediaWidgetProvider.class), null, null)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean access = getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(
                new ComponentName(this, MediaListenerService.class));
        accessStatus.setText(access ? "Granted" : "Not granted yet");
        accessButton.setEnabled(!access);

        boolean exempt = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        batteryStatus.setText(exempt ? "Done" : "Not set");
        batteryButton.setEnabled(!exempt);

        WidgetController.get(this).onWidgetsChanged();
    }

    private TextView heading(String s) {
        TextView t = text(s, 18);
        t.setPadding(0, dp(28), 0, dp(4));
        return t;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String s, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
