package dev.mediawidget;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns all widget state. Everything runs on one low-priority background thread and is
 * event driven: the app wakes on track / play-state changes, plus one tiny progress
 * update per second while the screen is on and unlocked. The wave animation and the
 * elapsed-time counter run inside the launcher and cost this app nothing.
 */
final class WidgetController {
    static final String ACTION_PLAY_PAUSE = "dev.mediawidget.PLAY_PAUSE";
    static final String ACTION_NEXT = "dev.mediawidget.NEXT";
    static final String ACTION_PREV = "dev.mediawidget.PREV";

    private static final String SPOTIFY = "com.spotify.music";
    private static final int MAX_LEVEL = 10000;

    private static WidgetController sInstance;

    static WidgetController get(Context context) {
        synchronized (WidgetController.class) {
            if (sInstance == null) sInstance = new WidgetController(context.getApplicationContext());
            return sInstance;
        }
    }

    private final Context ctx;
    private final Handler handler;
    private final AppWidgetManager awm;
    private final MediaSessionManager msm;
    private final NotificationManager nm;
    private final PowerManager power;
    private final KeyguardManager keyguard;
    private final ComponentName provider;
    private final ComponentName listener;
    private final float density;

    private final List<Tracked> tracked = new ArrayList<>();
    private final Map<String, Bitmap> bgCache = new HashMap<>();
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::onSessionsChanged;

    private boolean started;
    private int[] ids = new int[0];
    private boolean homeVisible;
    private MediaController current;

    private Bitmap art;
    private int artSig = -1;
    private int[] colors = Colors.DEFAULT;
    private Bitmap fill;

    // What the launcher currently shows, so redundant updates can be skipped.
    private boolean shownPlaying;
    private boolean shownRunning;
    private long shownBase; // elapsedRealtime at position 0 while running
    private long shownPos;

    private final Runnable fullUpdate = this::fullUpdate;
    private final Runnable tick = this::tick;
    private final Runnable verify = () -> {
        if (current != null) pushState(current.getPlaybackState());
    };

    private WidgetController(Context app) {
        ctx = app;
        HandlerThread t = new HandlerThread("widget", Process.THREAD_PRIORITY_DEFAULT);
        t.start();
        handler = new Handler(t.getLooper());
        awm = AppWidgetManager.getInstance(app);
        msm = app.getSystemService(MediaSessionManager.class);
        nm = app.getSystemService(NotificationManager.class);
        power = app.getSystemService(PowerManager.class);
        keyguard = app.getSystemService(KeyguardManager.class);
        provider = new ComponentName(app, MediaWidgetProvider.class);
        listener = new ComponentName(app, MediaListenerService.class);
        density = app.getResources().getDisplayMetrics().density;

        homeVisible = power.isInteractive() && !keyguard.isKeyguardLocked();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        app.registerReceiver(screenReceiver, f, null, handler);
    }

    // ---------------------------------------------------------------- entry points

    /** Called by the listener service, the widget provider and the settings screen. */
    void ensureStarted() {
        handler.post(() -> {
            if (!started && hasAccess()) {
                try {
                    msm.addOnActiveSessionsChangedListener(sessionsListener, listener, handler);
                    started = true;
                    onSessionsChanged(msm.getActiveSessions(listener));
                    return;
                } catch (SecurityException ignored) {
                    // Access was revoked between the check and the call; show setup state.
                }
            }
            scheduleFull(0);
        });
    }

    void onWidgetsChanged() {
        ensureStarted();
        scheduleFull(0);
    }

    void handleAction(String action) {
        handler.post(() -> {
            MediaController mc = current;
            if (mc == null) {
                // Nothing active: wake the last media app (normally Spotify) with a key event.
                int key = ACTION_NEXT.equals(action) ? KeyEvent.KEYCODE_MEDIA_NEXT
                        : ACTION_PREV.equals(action) ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        : KeyEvent.KEYCODE_MEDIA_PLAY;
                AudioManager am = ctx.getSystemService(AudioManager.class);
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                return;
            }
            MediaController.TransportControls tc = mc.getTransportControls();
            if (ACTION_NEXT.equals(action)) {
                tc.skipToNext();
            } else if (ACTION_PREV.equals(action)) {
                tc.skipToPrevious();
            } else {
                PlaybackState s = mc.getPlaybackState();
                boolean play = !isPlaying(s);
                if (play) tc.play(); else tc.pause();
                // Flip the button immediately so the tap feels instant; the real state from
                // the player confirms it, and verify() corrects it if the player refused.
                long dur = duration(mc.getMetadata());
                long pos = position(s, dur);
                RemoteViews rv = views();
                applyState(rv, play, play, pos, dur);
                partial(rv);
                scheduleTick();
                handler.removeCallbacks(verify);
                handler.postDelayed(verify, 1500);
            }
        });
    }

    // ---------------------------------------------------------------- sessions

    private void onSessionsChanged(List<MediaController> list) {
        for (Tracked t : tracked) t.mc.unregisterCallback(t);
        tracked.clear();
        if (list != null) {
            for (MediaController mc : list) {
                Tracked t = new Tracked(mc);
                mc.registerCallback(t, handler);
                tracked.add(t);
            }
        }
        reselect(true);
    }

    /** Prefers whatever is playing (Spotify first), otherwise sticks with the current player. */
    private void reselect(boolean force) {
        MediaController playingSpotify = null, playing = null, spotify = null, keep = null, first = null;
        for (Tracked t : tracked) {
            MediaController mc = t.mc;
            boolean isSpotify = SPOTIFY.equals(mc.getPackageName());
            boolean isPlaying = isPlaying(mc.getPlaybackState());
            if (first == null) first = mc;
            if (isSpotify && spotify == null) spotify = mc;
            if (isPlaying && isSpotify && playingSpotify == null) playingSpotify = mc;
            if (isPlaying && playing == null) playing = mc;
            if (same(mc, current)) keep = mc;
        }
        MediaController best = playingSpotify != null ? playingSpotify
                : playing != null ? playing
                : keep != null ? keep
                : spotify != null ? spotify
                : first;
        boolean changed = !same(best, current);
        current = best;
        if (changed || force) scheduleFull(0);
    }

    private void onCurrentState(PlaybackState s) {
        long dur = duration(current.getMetadata());
        boolean playing = isPlaying(s);
        boolean running = isRunning(s);
        long pos = position(s, dur);
        long expected = shownRunning ? SystemClock.elapsedRealtime() - shownBase : shownPos;
        // Players report position constantly; only touch the launcher on real changes.
        if (playing != shownPlaying || running != shownRunning || Math.abs(pos - expected) > 1500) {
            pushState(s);
        } else {
            scheduleTick();
        }
    }

    private final class Tracked extends MediaController.Callback {
        final MediaController mc;

        Tracked(MediaController mc) {
            this.mc = mc;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            MediaController before = current;
            reselect(false);
            if (same(mc, current) && same(before, current)) onCurrentState(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            // Players often send text first and art a moment later; coalesce them.
            if (same(mc, current)) scheduleFull(150);
        }
    }

    // ---------------------------------------------------------------- rendering

    private void scheduleFull(long delay) {
        handler.removeCallbacks(fullUpdate);
        handler.postDelayed(fullUpdate, delay);
    }

    private void fullUpdate() {
        int[] found = awm.getAppWidgetIds(provider);
        ids = found != null ? found : new int[0];
        if (ids.length == 0) {
            handler.removeCallbacks(tick);
            return;
        }
        if (!hasAccess()) {
            for (int id : ids) awm.updateAppWidget(id, setupViews(id));
            return;
        }

        MediaController mc = current;
        MediaMetadata md = mc != null ? mc.getMetadata() : null;
        CharSequence title = md != null ? md.getText(MediaMetadata.METADATA_KEY_TITLE) : null;
        boolean hasTrack = !TextUtils.isEmpty(title);
        updateArt(hasTrack ? rawArt(md) : null);

        for (int id : ids) {
            RemoteViews rv = views();
            int[] size = artSize(id);
            rv.setImageViewBitmap(R.id.bg, background(size[0], size[1]));
            rv.setImageViewBitmap(R.id.seek_fill, fill);
            rv.setInt(R.id.play_bg, "setColorFilter", colors[0]);

            Bitmap icon = mc != null ? appIcon(mc.getPackageName()) : null;
            rv.setViewVisibility(R.id.app_icon, icon != null ? View.VISIBLE : View.GONE);
            if (icon != null) rv.setImageViewBitmap(R.id.app_icon, icon);

            if (hasTrack) {
                rv.setTextViewText(R.id.title, title);
                rv.setTextViewText(R.id.artist, subtitle(md));
                PlaybackState s = mc.getPlaybackState();
                long dur = duration(md);
                rv.setTextViewText(R.id.duration, dur > 0 ? time(dur) : "");
                applyState(rv, isPlaying(s), isRunning(s), position(s, dur), dur);
            } else {
                rv.setTextViewText(R.id.title, ctx.getString(R.string.not_playing));
                rv.setTextViewText(R.id.artist, ctx.getString(R.string.tap_to_open));
                rv.setTextViewText(R.id.duration, time(0));
                applyState(rv, false, false, 0, 0);
            }

            rv.setOnClickPendingIntent(R.id.art_area, contentIntent(mc));
            rv.setOnClickPendingIntent(R.id.btn_play, action(ACTION_PLAY_PAUSE, 1));
            rv.setOnClickPendingIntent(R.id.btn_next, action(ACTION_NEXT, 2));
            rv.setOnClickPendingIntent(R.id.btn_prev, action(ACTION_PREV, 3));
            awm.updateAppWidget(id, rv);
        }
        scheduleTick();
    }

    private void pushState(PlaybackState s) {
        handler.removeCallbacks(verify);
        if (current == null) return;
        long dur = duration(current.getMetadata());
        RemoteViews rv = views();
        applyState(rv, isPlaying(s), isRunning(s), position(s, dur), dur);
        partial(rv);
        scheduleTick();
    }

    /** Play/pause look, wave vs flat line, time and progress. Shared by full and partial updates. */
    private void applyState(RemoteViews rv, boolean playing, boolean running, long pos, long dur) {
        rv.setImageViewResource(R.id.play_icon, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        rv.setViewVisibility(R.id.seek_wave, playing ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.seek_flat, playing ? View.GONE : View.VISIBLE);

        long base = SystemClock.elapsedRealtime() - pos;
        rv.setChronometer(R.id.elapsed_live, base, null, running);
        rv.setViewVisibility(R.id.elapsed_live, running ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.elapsed_static, running ? View.GONE : View.VISIBLE);
        if (!running) rv.setTextViewText(R.id.elapsed_static, time(pos));
        rv.setInt(R.id.seek_cover, "setImageLevel", MAX_LEVEL - level(pos, dur));

        shownPlaying = playing;
        shownRunning = running;
        shownBase = base;
        shownPos = pos;
    }

    private void tick() {
        if (!homeVisible || current == null || !shownRunning) return;
        long dur = duration(current.getMetadata());
        if (dur <= 0) return;
        long pos = Math.min(dur, SystemClock.elapsedRealtime() - shownBase);
        RemoteViews rv = views();
        rv.setInt(R.id.seek_cover, "setImageLevel", MAX_LEVEL - level(pos, dur));
        partial(rv);
        scheduleTick();
    }

    /** Next tick lands on the next whole second, in step with the launcher's chronometer. */
    private void scheduleTick() {
        handler.removeCallbacks(tick);
        if (!homeVisible || !shownRunning) return;
        long pos = SystemClock.elapsedRealtime() - shownBase;
        long delay = 1000 - (pos % 1000) + 10;
        handler.postDelayed(tick, delay);
    }

    private void partial(RemoteViews rv) {
        if (ids.length > 0) awm.partiallyUpdateAppWidget(ids, rv);
    }

    private RemoteViews views() {
        return new RemoteViews(ctx.getPackageName(), R.layout.widget_media);
    }

    private RemoteViews setupViews(int id) {
        RemoteViews rv = views();
        int[] size = artSize(id);
        updateArt(null);
        rv.setImageViewBitmap(R.id.bg, background(size[0], size[1]));
        rv.setImageViewBitmap(R.id.seek_fill, fill);
        rv.setInt(R.id.play_bg, "setColorFilter", colors[0]);
        rv.setViewVisibility(R.id.app_icon, View.GONE);
        rv.setTextViewText(R.id.title, ctx.getString(R.string.setup_title));
        rv.setTextViewText(R.id.artist, ctx.getString(R.string.setup_sub));
        rv.setTextViewText(R.id.duration, time(0));
        applyState(rv, false, false, 0, 0);
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        for (int v : new int[] {R.id.art_area, R.id.btn_play, R.id.btn_next, R.id.btn_prev}) {
            rv.setOnClickPendingIntent(v, open);
        }
        return rv;
    }

    private void updateArt(Bitmap raw) {
        if (raw != null && raw.getConfig() == Bitmap.Config.HARDWARE) {
            raw = raw.copy(Bitmap.Config.ARGB_8888, false);
        }
        int sig = WidgetRenderer.signature(raw);
        if (sig == artSig && fill != null) return;
        artSig = sig;
        art = WidgetRenderer.normalizeArt(raw);
        colors = art != null ? Colors.extract(art) : Colors.DEFAULT;
        fill = WidgetRenderer.fill(colors);
        bgCache.clear();
    }

    private Bitmap background(int w, int h) {
        String key = w + "x" + h;
        Bitmap b = bgCache.get(key);
        if (b == null) {
            b = WidgetRenderer.background(art, w, h, WidgetRenderer.RADIUS_DP * density);
            bgCache.put(key, b);
        }
        return b;
    }

    /** Pixel size of the art area, from the launcher-reported widget size (portrait). */
    private int[] artSize(int id) {
        Bundle o = awm.getAppWidgetOptions(id);
        int wDp = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) : 0;
        int hDp = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : 0;
        if (wDp <= 0) wDp = 330;
        if (hDp <= 0) hDp = 190;
        float artDp = Math.max(48f, hDp - WidgetRenderer.PANEL_DP);
        return new int[] {Math.round(wDp * density), Math.round(artDp * density)};
    }

    private Bitmap appIcon(String pkg) {
        if (pkg == null) return null;
        if (iconCache.containsKey(pkg)) return iconCache.get(pkg);
        Bitmap b = null;
        try {
            b = WidgetRenderer.icon(ctx.getPackageManager().getApplicationIcon(pkg), Math.round(22 * density));
        } catch (Exception ignored) {
            // Package not visible or gone: just hide the icon.
        }
        iconCache.put(pkg, b);
        return b;
    }

    private PendingIntent contentIntent(MediaController mc) {
        if (mc != null && mc.getSessionActivity() != null) return mc.getSessionActivity();
        String pkg = mc != null ? mc.getPackageName() : SPOTIFY;
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) launch = ctx.getPackageManager().getLaunchIntentForPackage(SPOTIFY);
        if (launch == null) launch = new Intent(ctx, MainActivity.class);
        return PendingIntent.getActivity(ctx, 10, launch,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent action(String action, int code) {
        Intent i = new Intent(ctx, ActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, code, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---------------------------------------------------------------- screen state

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                homeVisible = false;
                handler.removeCallbacks(tick);
            } else if (Intent.ACTION_USER_PRESENT.equals(a)
                    || (Intent.ACTION_SCREEN_ON.equals(a) && !keyguard.isKeyguardLocked())) {
                homeVisible = true;
                tick(); // catch the bar up immediately, then resume the 1 s cadence
            }
        }
    };

    // ---------------------------------------------------------------- helpers

    private boolean hasAccess() {
        return nm.isNotificationListenerAccessGranted(listener);
    }

    private static boolean same(MediaController a, MediaController b) {
        if (a == b) return true;
        return a != null && b != null && a.getSessionToken().equals(b.getSessionToken());
    }

    static boolean isPlaying(PlaybackState s) {
        if (s == null) return false;
        int st = s.getState();
        return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING;
    }

    private static boolean isRunning(PlaybackState s) {
        return s != null && s.getState() == PlaybackState.STATE_PLAYING && s.getPlaybackSpeed() > 0;
    }

    private static long position(PlaybackState s, long dur) {
        if (s == null) return 0;
        long p = s.getPosition();
        long updated = s.getLastPositionUpdateTime();
        if (isRunning(s) && updated > 0) {
            p += (long) ((SystemClock.elapsedRealtime() - updated) * s.getPlaybackSpeed());
        }
        if (p < 0) p = 0;
        if (dur > 0 && p > dur) p = dur;
        return p;
    }

    private static long duration(MediaMetadata md) {
        return md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
    }

    private static int level(long pos, long dur) {
        if (dur <= 0) return 0;
        return (int) Math.max(0, Math.min(MAX_LEVEL, pos * MAX_LEVEL / dur));
    }

    private static String time(long ms) {
        return DateUtils.formatElapsedTime(ms / 1000);
    }

    private static CharSequence subtitle(MediaMetadata md) {
        CharSequence a = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(a)) a = md.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(a)) a = md.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return a != null ? a : "";
    }

    private static Bitmap rawArt(MediaMetadata md) {
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }
}
