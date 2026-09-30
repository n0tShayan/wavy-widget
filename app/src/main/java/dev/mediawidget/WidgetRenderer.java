package dev.mediawidget;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Bitmaps pushed to the launcher. Rendered once per track / widget size, never per frame. */
final class WidgetRenderer {
    /** Must match @color/panel and PANEL in tools/gen_wave.py. */
    static final int PANEL = 0xFF101012;
    static final float PANEL_DP = 92f;
    static final float RADIUS_DP = 26f;
    /** Art is kept at 480 px, so a wider background adds memory (here, the launcher's and
     *  system_server's copies) but no detail; the launcher scales it up. */
    private static final int MAX_BG_WIDTH = 720;

    private WidgetRenderer() {}

    /**
     * Album art centre-cropped to the art area, rounded top corners, fading into the panel
     * colour at the bottom so the overlaid text and the panel below blend seamlessly.
     */
    static Bitmap background(Bitmap art, int w, int h, float radius) {
        float scale = Math.min(1f, (float) MAX_BG_WIDTH / w);
        w = Math.max(1, Math.round(w * scale));
        h = Math.max(1, Math.round(h * scale));
        radius *= scale;

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF shape = new RectF(0, 0, w, h + radius); // push the bottom corners off-canvas

        if (art != null) {
            BitmapShader shader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max((float) w / art.getWidth(), (float) h / art.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((w - art.getWidth() * s) / 2f, (h - art.getHeight() * s) / 2f);
            shader.setLocalMatrix(m);
            p.setShader(shader);
        } else {
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF2C2C34, PANEL, Shader.TileMode.CLAMP));
        }
        c.drawRoundRect(shape, radius, radius, p);

        int clear = PANEL & 0x00FFFFFF;
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[] {0x47000000, clear, clear, (PANEL & 0x00FFFFFF) | 0xD9000000, PANEL},
                new float[] {0f, 0.28f, 0.34f, 0.78f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRoundRect(shape, radius, radius, p);
        return out;
    }

    /** 1px-high horizontal gradient; stretched under the wave mask by the launcher. */
    static Bitmap fill(int[] colors) {
        Bitmap out = Bitmap.createBitmap(96, 1, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 96, 0, colors[0], colors[1], Shader.TileMode.CLAMP));
        new Canvas(out).drawRect(0, 0, 96, 1, p);
        return out;
    }

    static Bitmap icon(Drawable d, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, size, size);
        d.draw(new Canvas(out));
        return out;
    }

    /** Downscaled software copy: small enough to keep around for re-renders on resize. */
    static Bitmap normalizeArt(Bitmap src) {
        if (src == null) return null;
        int max = 480;
        Bitmap b = src;
        if (b.getConfig() == Bitmap.Config.HARDWARE) b = b.copy(Bitmap.Config.ARGB_8888, false);
        int big = Math.max(b.getWidth(), b.getHeight());
        if (big > max) {
            float s = (float) max / big;
            b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * s), Math.round(b.getHeight() * s), true);
        }
        return b;
    }

    /** Cheap identity for a cover: size plus a 5x5 grid of samples. */
    static int signature(Bitmap b) {
        if (b == null) return 0;
        int w = b.getWidth(), h = b.getHeight();
        int sig = w * 31 + h;
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                sig = sig * 31 + b.getPixel((w - 1) * x / 4, (h - 1) * y / 4);
            }
        }
        return sig == 0 ? 1 : sig;
    }
}
