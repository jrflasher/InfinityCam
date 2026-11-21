package com.raisproject.infinitycam;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Real, simple post-processing effects (no Samsung proprietary code).
 * Portrait: sharp centre + blurred edges (bokeh simulation, strength 0-100).
 * Food: saturation boost + warm vignette. Night: brightness lift.
 */
public class ImageFx {

    /** Fast blur by downscale + upscale. */
    public static Bitmap blur(Bitmap src, int factor) {
        if (factor < 2) factor = 2;
        int w = Math.max(1, src.getWidth() / factor);
        int h = Math.max(1, src.getHeight() / factor);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        Bitmap out = Bitmap.createScaledBitmap(small, src.getWidth(), src.getHeight(), true);
        if (small != src) small.recycle();
        return out;
    }

    /** Portrait bokeh: centre stays sharp, edges blurred. strength 0..100. */
    public static Bitmap portraitBokeh(Bitmap src, int strength) {
        if (strength <= 0) return src;
        int factor = 4 + strength / 20; // 4..9
        Bitmap blurred = blur(src, factor);
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(blurred, 0, 0, null);
        // Mask: sharp circle in the middle, blurred outside.
        Bitmap sharp = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sharp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = src.getWidth() / 2f, cy = src.getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.95f;
        RadialGradient g = new RadialGradient(cx, cy, r,
                new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        p.setShader(g);
        sc.drawCircle(cx, cy, r, p);
        Paint maskPaint = new Paint();
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        Bitmap maskedSharp = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas mc = new Canvas(maskedSharp);
        mc.drawBitmap(src, 0, 0, null);
        mc.drawBitmap(sharp, 0, 0, maskPaint);
        c.drawBitmap(maskedSharp, 0, 0, null);
        blurred.recycle(); sharp.recycle(); maskedSharp.recycle();
        return out;
    }

    /** Food mode: +saturation, slight warm tint, soft vignette. */
    public static Bitmap food(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.45f);
        // warm tint
        ColorMatrix warm = new ColorMatrix(new float[]{
                1.08f, 0, 0, 0, 6,
                0, 1.0f, 0, 0, 0,
                0, 0, 0.94f, 0, 0,
                0, 0, 0, 1, 0});
        cm.postConcat(warm);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src, 0, 0, p);
        // vignette
        Paint vp = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = src.getWidth() / 2f, cy = src.getHeight() / 2f;
        float r = (float) Math.hypot(cx, cy);
        RadialGradient vg = new RadialGradient(cx, cy, r,
                new int[]{0x00000000, 0x33000000},
                new float[]{0.55f, 1f}, Shader.TileMode.CLAMP);
        vp.setShader(vg);
        c.drawRect(0, 0, src.getWidth(), src.getHeight(), vp);
        return out;
    }

    /** Night mode: lift brightness / shadows a bit. */
    public static Bitmap night(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.25f, 0, 0, 0, 18,
                0, 1.25f, 0, 0, 18,
                0, 0, 1.25f, 0, 18,
                0, 0, 0, 1, 0});
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }
}
