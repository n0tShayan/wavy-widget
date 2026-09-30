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
 * Owns all widget state. Everything runs on one background thread and is event driven:
 * the app wakes on track / play-state changes, plus a tiny progress update at most once a
 * second (only when the bar would visibly move) while the screen is on and unlocked. The
 * wave, the crossfades, the play / pause morph and the elapsed-time counter all run inside
 * the launcher and cost this app nothing.
 */
final class WidgetController {
    static final String ACTION_PLAY_PAUSE = "dev.mediawidget.PLAY_PAUSE";
    static final String ACTION_NEXT = "dev.mediawidget.NEXT";
    static final String ACTION_PREV = "dev.mediawidget.PREV";

    private static final String SPOTIFY = "com.spotify.music";
    private static final int MAX_LEVEL = 10000;
    /** Longest play / pause transition (wave grow, 520 ms) plus margin; see tools/gen_wave.py. */
    private static final long SETTLE_MS = 600;
    /** LayoutTransition crossfade: 300 ms fade out, 300 ms delayed fade in, plus margin. */
    private static final long RELEASE_MS = 900;
    /** Panel padding, time labels and margins beside the seek bar (widget_media.xml). */
    private static final float SEEK_CHROME_DP = 114f;
    private static final int NONE = Integer.MIN_VALUE;

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
    private Tracked current;

    private Bitmap art;
    private int artSig = NONE;
    private int[] colors = Colors.DEFAULT;
    private Bitmap fill;
    private long trackDur;

    // What the launcher currently shows, so redundant updates can be skipped.
    private String renderedKey;
    private boolean forceFull;
    private int barPx = 1;
    private boolean stateShown;
    private boolean shownPlaying;
    private boolean shownRunning;
    private long shownBase; // elapsedRealtime at position 0 while running
    private long shownPos;
    private long shownDur;
    private int shownPx = -1;
    private int shownThumb;
    private long transitionUntil; // uptimeMillis; morph views stay up until then

    // Double-buffered layers (a = 0, b = 1) that the launcher crossfades on a flip.
    private int artLayer;
    private int renderedArtSig = NONE;
    private int textLayer;
    private String shownTitle;
    private String shownArtist;

    private final Runnable fullUpdate = this::fullUpdate;
    private final Runnable tick = this::tick;
    private final Runnable verify = () -> {
        if (current != null) pushState(current.st);
    };
    private final Runnable pausedLater = () -> {
        if (current != null) pushState(current.st);
    };
    /** Swaps the play / pause morph views for their static twins once they have finished. */
    private final Runnable settle = () -> {
        transitionUntil = 0;
        if (ids.length == 0) return;
        RemoteViews rv = views();
        showPlayState(rv, shownPlaying, false);
        partial(rv);
    };
    /** Drops the covered art layer from the launcher's memory once the crossfade is done. */
    private final Runnable release = () -> {
        if (ids.length == 0) return;
        RemoteViews rv = views();
        rv.setImageViewResource(artLayer == 1 ? R.id.bg_a : R.id.bg_b, 0);
        partial(rv);
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

    /** Called by the listener service, the widget provider, taps and the settings screen. */
    void ensureStarted() {
        handler.post(() -> {
            if (started) return;
            if (hasAccess()) {
                try {
                    msm.addOnActiveSessionsChangedListener(sessionsListener, listener, handler);
                    started = true;
                    onSessionsChanged(msm.getActiveSessions(listener));
                    return;
                } catch (SecurityException ignored) {
                    // Access was revoked between the check and the call; show setup state.
                }
            }
            scheduleFull(0, true);
        });
    }

    void onWidgetsChanged() {
        ensureStarted();
        scheduleFull(0, true);
    }

    void handleAction(String action) {
        handler.post(() -> {
            Tracked t = current;
            if (t == null) {
                // Nothing active: wake the last media app (normally Spotify) with a key event.
                int key = ACTION_NEXT.equals(action) ? KeyEvent.KEYCODE_MEDIA_NEXT
                        : ACTION_PREV.equals(action) ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        : KeyEvent.KEYCODE_MEDIA_PLAY;
                AudioManager am = ctx.getSystemService(AudioManager.class);
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                return;
            }
            handler.removeCallbacks(pausedLater);
            MediaController.TransportControls tc = t.mc.getTransportControls();
            if (ACTION_NEXT.equals(action)) {
                tc.skipToNext();
            } else if (ACTION_PREV.equals(action)) {
                tc.skipToPrevious();
            } else {
                boolean play = !isPlaying(t.st);
                if (play) tc.play(); else tc.pause();
                // Flip the button immediately so the tap feels instant; the real state from
                // the player confirms it, and verify() corrects it if the player refused.
                RemoteViews rv = views();
                applyState(rv, play, play, position(t.st, trackDur), trackDur);
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
        Tracked playingSpotify = null, playing = null, spotify = null, keep = null, first = null;
        for (Tracked t : tracked) {
            boolean isSpotify = SPOTIFY.equals(t.mc.getPackageName());
            boolean isPlaying = isPlaying(t.st);
            if (first == null) first = t;
            if (isSpotify && spotify == null) spotify = t;
            if (isPlaying && isSpotify && playingSpotify == null) playingSpotify = t;
            if (isPlaying && playing == null) playing = t;
            if (same(t, current)) keep = t;
        }
        Tracked best = playingSpotify != null ? playingSpotify
                : playing != null ? playing
                : keep != null ? keep
                : spotify != null ? spotify
                : first;
        boolean changed = !same(best, current);
        current = best;
        if (changed || force) scheduleFull(0, true);
    }

    private void onCurrentState() {
        PlaybackState s = current.st;
        boolean playing = isPlaying(s);
        boolean running = isRunning(s);
        long pos = position(s, trackDur);
        if (shownPlaying && !playing) {
            // Players dip out of PLAYING for a moment on skips and seeks; don't flash the
            // pause look (and collapse the wave) unless it sticks.
            if (!handler.hasCallbacks(pausedLater)) handler.postDelayed(pausedLater, 400);
            return;
        }
        handler.removeCallbacks(pausedLater);
        long expected = shownPosNow();
        // Players report position constantly; only touch the launcher on real changes.
        if (playing != shownPlaying || running != shownRunning || Math.abs(pos - expected) > 1500) {
            pushState(s);
        } else {
            scheduleTick();
        }
    }

    /**
     * A session plus its latest metadata and state, kept from the callbacks. Reading them
     * back from the MediaController is a binder call each time, and the metadata carries
     * the full album bitmap.
     */
    private final class Tracked extends MediaController.Callback {
        final MediaController mc;
        MediaMetadata md;
        PlaybackState st;

        Tracked(MediaController mc) {
            this.mc = mc;
            md = mc.getMetadata();
            st = mc.getPlaybackState();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            st = state;
            Tracked before = current;
            reselect(false);
            if (this == current && before == current) onCurrentState();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            md = metadata;
            // Players often send text first and art a moment later; coalesce them.
            if (this == current) scheduleFull(150, false);
        }
    }

    // ---------------------------------------------------------------- rendering

    private void scheduleFull(long delay, boolean force) {
        forceFull |= force;
        handler.removeCallbacks(fullUpdate);
        handler.postDelayed(fullUpdate, delay);
    }

    private void fullUpdate() {
        boolean force = forceFull;
        forceFull = false;
        int[] found = awm.getAppWidgetIds(provider);
        ids = found != null ? found : new int[0];
        if (ids.length == 0) {
            handler.removeCallbacks(tick);
            renderedKey = null;
            return;
        }

        boolean access = hasAccess();
        Tracked t = access ? current : null;
        MediaMetadata md = t != null ? t.md : null;
        CharSequence rawTitle = md != null ? md.getText(MediaMetadata.METADATA_KEY_TITLE) : null;
        boolean hasTrack = !TextUtils.isEmpty(rawTitle);
        updateArt(hasTrack ? rawArt(md) : null);
        trackDur = hasTrack ? duration(md) : 0;

        String title, artist;
        if (!access) {
            title = ctx.getString(R.string.setup_title);
            artist = ctx.getString(R.string.setup_sub);
        } else if (hasTrack) {
            title = rawTitle.toString();
            artist = subtitle(md).toString();
        } else {
            title = ctx.getString(R.string.not_playing);
            artist = ctx.getString(R.string.tap_to_open);
        }
        String pkg = t != null ? t.mc.getPackageName() : null;

        int[][] sizes = new int[ids.length][];
        int bar = 1;
        StringBuilder key = new StringBuilder(128).append(access).append('\n').append(title)
                .append('\n').append(artist).append('\n').append(trackDur).append('\n')
                .append(artSig).append('\n').append(pkg);
        for (int i = 0; i < ids.length; i++) {
            sizes[i] = widgetDp(ids[i]);
            key.append('\n').append(ids[i]).append(':').append(sizes[i][0]).append('x').append(sizes[i][1]);
            bar = Math.max(bar, Math.round((sizes[i][0] - SEEK_CHROME_DP) * density));
        }
        barPx = bar;
        String k = key.toString();
        if (!force && k.equals(renderedKey)) {
            // A repeat of what's on screen (players re-send metadata often): no bitmaps.
            if (t != null) onCurrentState();
            return;
        }
        renderedKey = k;

        if (renderedArtSig != NONE && artSig != renderedArtSig) {
            artLayer ^= 1;
            handler.removeCallbacks(release);
            handler.postDelayed(release, RELEASE_MS);
        }
        renderedArtSig = artSig;
        if (shownTitle != null && (!title.equals(shownTitle) || !artist.equals(shownArtist))) {
            textLayer ^= 1;
        }
        shownTitle = title;
        shownArtist = artist;

        boolean b = artLayer == 1;
        boolean tb = textLayer == 1;
        PlaybackState s = t != null ? t.st : null;
        Bitmap icon = t != null ? appIcon(pkg) : null;
        PendingIntent open = access ? null : PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent content = access ? contentIntent(t) : open;

        for (int i = 0; i < ids.length; i++) {
            RemoteViews rv = views();
            int[] px = artPx(sizes[i]);
            // Layer b sits on top: showing it fades it in, hiding it fades it out.
            rv.setViewVisibility(R.id.bg_b, b ? View.VISIBLE : View.GONE);
            rv.setViewVisibility(R.id.fill_b, b ? View.VISIBLE : View.GONE);
            rv.setViewVisibility(R.id.play_bg_b, b ? View.VISIBLE : View.GONE);
            rv.setImageViewBitmap(b ? R.id.bg_b : R.id.bg_a, background(px[0], px[1]));
            rv.setImageViewBitmap(b ? R.id.fill_b : R.id.fill_a, fill);
            rv.setInt(b ? R.id.play_bg_b : R.id.play_bg_a, "setColorFilter", colors[0]);

            rv.setViewVisibility(R.id.text_a, tb ? View.GONE : View.VISIBLE);
            rv.setViewVisibility(R.id.text_b, tb ? View.VISIBLE : View.GONE);
            rv.setTextViewText(tb ? R.id.title_b : R.id.title_a, title);
            rv.setTextViewText(tb ? R.id.artist_b : R.id.artist_a, artist);

            rv.setViewVisibility(R.id.app_icon, icon != null ? View.VISIBLE : View.GONE);
            if (icon != null) rv.setImageViewBitmap(R.id.app_icon, icon);

            if (hasTrack) {
                rv.setTextViewText(R.id.duration, trackDur > 0 ? time(trackDur) : "");
                if (handler.hasCallbacks(pausedLater)) {
                    // Mid-skip dip (see onCurrentState): keep the playing look for now.
                    applyState(rv, shownPlaying, shownRunning, Math.min(shownPosNow(), trackDur), trackDur);
                } else {
                    applyState(rv, isPlaying(s), isRunning(s), position(s, trackDur), trackDur);
                }
            } else {
                rv.setTextViewText(R.id.duration, time(0));
                applyState(rv, false, false, 0, 0);
            }

            rv.setOnClickPendingIntent(R.id.art_area, content);
            rv.setOnClickPendingIntent(R.id.btn_play, access ? action(ACTION_PLAY_PAUSE, 1) : open);
            rv.setOnClickPendingIntent(R.id.btn_next, access ? action(ACTION_NEXT, 2) : open);
            rv.setOnClickPendingIntent(R.id.btn_prev, access ? action(ACTION_PREV, 3) : open);
            awm.updateAppWidget(ids[i], rv);
        }
        scheduleTick();
    }

    private void pushState(PlaybackState s) {
        handler.removeCallbacks(verify);
        handler.removeCallbacks(pausedLater);
        if (current == null) return;
        RemoteViews rv = views();
        applyState(rv, isPlaying(s), isRunning(s), position(s, trackDur), trackDur);
        partial(rv);
        scheduleTick();
    }

    /** Play/pause look, wave vs flat line, time and progress. Shared by full and partial updates. */
    private void applyState(RemoteViews rv, boolean playing, boolean running, long pos, long dur) {
        long now = SystemClock.uptimeMillis();
        if (stateShown && playing != shownPlaying && homeVisible) {
            // Show the morphing views; the launcher runs them, settle() swaps in static ones.
            transitionUntil = now + SETTLE_MS;
            handler.removeCallbacks(settle);
            handler.postDelayed(settle, SETTLE_MS);
        }
        showPlayState(rv, playing, now < transitionUntil);

        long base = SystemClock.elapsedRealtime() - pos;
        rv.setChronometer(R.id.elapsed_live, base, null, running);
        rv.setViewVisibility(R.id.elapsed_live, running ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.elapsed_static, running ? View.GONE : View.VISIBLE);
        if (!running) rv.setTextViewText(R.id.elapsed_static, time(pos));

        stateShown = true;
        shownPlaying = playing;
        shownRunning = running;
        shownBase = base;
        shownPos = pos;
        shownDur = dur;
        progress(rv, pos, dur, true);
    }

    /**
     * Only one of each pair is visible. While {@code moving}, the animated versions show:
     * the wave shrinking to a line, and the play / pause icon morphing. Visibility changes
     * are what start them, and re-sending the same visibility doesn't restart them.
     */
    private static void showPlayState(RemoteViews rv, boolean playing, boolean moving) {
        rv.setViewVisibility(R.id.seek_wave, playing ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.seek_shrink, !playing && moving ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.seek_flat, !playing && !moving ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.icon_to_pause, playing && moving ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.icon_to_play, !playing && moving ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.play_icon, moving ? View.GONE : View.VISIBLE);
        rv.setImageViewResource(R.id.play_icon, playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    /** Moves the cover and handle to {@code pos}; the handle takes the gradient's colour there. */
    private void progress(RemoteViews rv, long pos, long dur, boolean all) {
        int lvl = level(pos, dur);
        int cover = Math.max(1, MAX_LEVEL - lvl); // level 0 would hide the handle entirely
        rv.setInt(R.id.seek_cover, "setImageLevel", cover);
        rv.setInt(R.id.seek_thumb, "setImageLevel", cover);
        int c = blend(colors[0], colors[1], lvl / (float) MAX_LEVEL);
        if (all || c != shownThumb) rv.setInt(R.id.seek_thumb, "setColorFilter", c);
        shownThumb = c;
        shownPx = pixel(pos, dur);
    }

    private void tick() {
        if (!homeVisible || current == null || !shownRunning || shownDur <= 0) return;
        long pos = Math.min(shownDur, SystemClock.elapsedRealtime() - shownBase);
        if (pixel(pos, shownDur) != shownPx) {
            RemoteViews rv = views();
            progress(rv, pos, shownDur, false);
            partial(rv);
        }
        scheduleTick();
    }

    /**
     * Next tick lands on the first whole second (in step with the launcher's chronometer)
     * at which the bar has moved at least a pixel: every second for songs, less often for
     * long podcasts where a second is a fraction of a pixel.
     */
    private void scheduleTick() {
        handler.removeCallbacks(tick);
        if (!homeVisible || !shownRunning || shownDur <= 0) return;
        long pos = SystemClock.elapsedRealtime() - shownBase;
        if (pos >= shownDur) return;
        long nextPx = (shownPx + 1L) * shownDur / barPx + 1;
        long target = Math.max(pos + 1, nextPx);
        target = (target + 999) / 1000 * 1000;
        handler.postDelayed(tick, target - pos + 10);
    }

    private long shownPosNow() {
        return shownRunning ? SystemClock.elapsedRealtime() - shownBase : shownPos;
    }

    private void partial(RemoteViews rv) {
        if (ids.length > 0) awm.partiallyUpdateAppWidget(ids, rv);
    }

    private RemoteViews views() {
        return new RemoteViews(ctx.getPackageName(), R.layout.widget_media);
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

    /** Launcher-reported widget size in dp (portrait). */
    private int[] widgetDp(int id) {
        Bundle o = awm.getAppWidgetOptions(id);
        int wDp = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) : 0;
        int hDp = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : 0;
        return new int[] {wDp > 0 ? wDp : 330, hDp > 0 ? hDp : 190};
    }

    /** Pixel size of the art area. */
    private int[] artPx(int[] dp) {
        float artDp = Math.max(48f, dp[1] - WidgetRenderer.PANEL_DP);
        return new int[] {Math.round(dp[0] * density), Math.round(artDp * density)};
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

    private PendingIntent contentIntent(Tracked t) {
        PendingIntent session = t != null ? t.mc.getSessionActivity() : null;
        if (session != null) return session;
        String pkg = t != null ? t.mc.getPackageName() : SPOTIFY;
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

    private static boolean same(Tracked a, Tracked b) {
        if (a == b) return true;
        return a != null && b != null && a.mc.getSessionToken().equals(b.mc.getSessionToken());
    }

    static boolean isPlaying(PlaybackState s) {
        if (s == null) return false;
        switch (s.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
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

    private int pixel(long pos, long dur) {
        if (dur <= 0) return 0;
        return (int) Math.max(0, Math.min(barPx, pos * barPx / dur));
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
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
