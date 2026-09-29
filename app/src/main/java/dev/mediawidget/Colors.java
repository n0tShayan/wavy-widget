package dev.mediawidget;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Tiny album-art colour extractor (one 24x24 pass per track, ~0.2 ms). */
final class Colors {
    static final int[] DEFAULT = {0xFF1ED760, 0xFF4FC3F7};

    private static final int BUCKETS = 24; // 15 degrees of hue each

    private Colors() {}

    /** Returns {primary, secondary}, both tuned to stand out on the dark panel. */
    static int[] extract(Bitmap art) {
        Bitmap small = Bitmap.createScaledBitmap(art, 24, 24, true);
        int[] px = new int[24 * 24];
        small.getPixels(px, 0, 24, 0, 0, 24, 24);
        if (small != art) small.recycle();

        float[] weight = new float[BUCKETS];
        float[] sumH = new float[BUCKETS], sumS = new float[BUCKETS], sumV = new float[BUCKETS];
        float[] hsv = new float[3];
        float total = 0;
        for (int c : px) {
            Color.colorToHSV(c, hsv);
            if (hsv[2] < 0.15f || hsv[1] < 0.12f) continue; // near-black / grey pixels carry no hue
            float w = hsv[1] * hsv[2];
            int b = Math.min(BUCKETS - 1, (int) (hsv[0] / (360f / BUCKETS)));
            weight[b] += w;
            sumH[b] += hsv[0] * w;
            sumS[b] += hsv[1] * w;
            sumV[b] += hsv[2] * w;
            total += w;
        }
        if (total < 8f) return new int[] {0xFFE6E6EA, 0xFF9EA3B0}; // greyscale cover

        int b1 = 0;
        for (int i = 1; i < BUCKETS; i++) if (weight[i] > weight[b1]) b1 = i;
        int b2 = -1;
        for (int i = 0; i < BUCKETS; i++) {
            int d = Math.abs(i - b1);
            if (Math.min(d, BUCKETS - d) < 3) continue; // at least 45 degrees apart
            if (b2 < 0 || weight[i] > weight[b2]) b2 = i;
        }

        int c1 = tune(sumH[b1] / weight[b1], sumS[b1] / weight[b1], sumV[b1] / weight[b1]);
        int c2;
        if (b2 >= 0 && weight[b2] > weight[b1] * 0.12f) {
            c2 = tune(sumH[b2] / weight[b2], sumS[b2] / weight[b2], sumV[b2] / weight[b2]);
        } else {
            // Single-hue cover: use an analogous shade so the wave is still a gradient.
            c2 = tune((sumH[b1] / weight[b1] + 32f) % 360f, sumS[b1] / weight[b1] * 0.8f, 1f);
        }
        return new int[] {c1, c2};
    }

    private static int tune(float h, float s, float v) {
        return Color.HSVToColor(new float[] {h, clamp(s, 0.40f, 0.85f), clamp(v, 0.88f, 1f)});
    }

    private static float clamp(float x, float lo, float hi) {
        return x < lo ? lo : Math.min(x, hi);
    }
}
