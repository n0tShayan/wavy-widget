package dev.mediawidget;

import android.content.ComponentName;
import android.service.notification.NotificationListenerService;

/**
 * Exists only so the system grants MediaSession access and keeps the process bound.
 * Notification callbacks are intentionally not overridden.
 */
public class MediaListenerService extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        WidgetController.get(this).ensureStarted();
    }

    @Override
    public void onListenerDisconnected() {
        requestRebind(new ComponentName(this, MediaListenerService.class));
    }
}
