package dev.mediawidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Play/pause, next, previous, seek-zone and like taps from the widget. */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WidgetController c = WidgetController.get(context);
        c.ensureStarted();
        c.handleAction(intent.getAction(), intent.getIntExtra(WidgetController.EXTRA_ZONE, -1));
    }
}
