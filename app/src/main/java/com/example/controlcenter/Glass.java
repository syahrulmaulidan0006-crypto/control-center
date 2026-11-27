package com.example.controlcenter;

import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Efek kaca: isian tembus pandang, kilau di atas, dan tepi yang memantulkan cahaya. */
class Glass extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float radius, density;
    private final int tint;
    private boolean reading;

    Glass(Context context, int radiusDp, int tint) {
        density = context.getResources().getDisplayMetrics().density;
        radius = radiusDp * density;
        this.tint = tint;
    }

    /** Untuk permukaan berisi tulisan: latarnya sedikit lebih buram. */
    Glass reading() {
        reading = true;
        return this;
    }

    @Override public void draw(Canvas c) {
        box.set(getBounds());
        Callback host = getCallback();
        if (host instanceof android.view.View) Frost.begin((android.view.View) host);
        draw(c, paint, box, Math.min(radius, Math.min(box.width(), box.height()) / 2),
                density, tint, reading);
        Frost.end();
    }

    /** Gaya yang sedang dipakai; diperbarui setiap panel, popup, atau jendela dibuat. */
    static int style = PopupGlass.LIQUID;

    /** Kebeningan 0 (pekat) sampai 1 (sebening kaca). */
    static float clear = 0.8f;

    static void load(Context c) {
        style = PopupGlass.chosen(c);
        clear = PopupGlass.clearness(c) / 100f;
    }

    /** Warna tipis ikut memudar sesuai kebeningan; warna pekat (tombol aktif) tetap. */
    private static int thin(int color, float strength) {
        int alpha = color >>> 24;
        if (alpha >= 0xC0) return color;
        return Theme.alpha(color, Math.round(alpha * (1 - strength * clear)));
    }

    private static int[] thin(int[] colors, float strength) {
        for (int i = 0; i < colors.length; i++) colors[i] = thin(colors[i], strength);
        return colors;
    }

    static void draw(Canvas c, Paint paint, RectF r, float radius, float density, int tint) {
        draw(c, paint, r, radius, density, tint, false);
    }

    static void draw(Canvas c, Paint paint, RectF r, float radius, float density, int tint,
            boolean reading) {
        boolean frosted = style == PopupGlass.FROSTED;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        if (Frost.active()) {
            if (Frost.isInner()) {
                // Di atas kaca lain cukup isian tipis, tanpa lapisan latar kedua.
                paint.setColor(0x1CFFFFFF);
                c.drawRoundRect(r, radius, radius, paint);
            } else {
                Frost.draw(c, r, radius, density, clear, reading);
            }
            // Warna khas benda (tombol aktif, tombol hapus) tetap di atas kacanya.
            paint.setColor(thin(tint, 0.9f));
            c.drawRoundRect(r, radius, radius, paint);
            if (!Frost.isInner()) Frost.rim(c, r, radius, density);
            return;
        }
        paint.setColor(thin(tint, 0.82f));
        c.drawRoundRect(r, radius, radius, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, thin(frosted
                ? new int[]{0x2EFFFFFF, 0x22FFFFFF, 0x1CFFFFFF, 0x22FFFFFF}
                : new int[]{0x24FFFFFF, 0x08FFFFFF, 0x05FFFFFF, 0x16FFFFFF},
                frosted ? 0.6f : 0.75f), new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, paint);
        shine(c, paint, r, radius, density);
    }

    /** Satu garis tipis di tepi kaca. */
    static void shine(Canvas c, Paint paint, RectF r, float radius, float density) {
        if (Frost.active()) {
            if (!Frost.isInner()) Frost.rim(c, r, radius, density);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFFFFFFF);
        if (style == PopupGlass.FROSTED) {
            paint.setShader(null);
            paint.setColor(0x4DFFFFFF);
        } else {
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0xB3FFFFFF, 0x26FFFFFF, 0x1FFFFFFF, 0x73FFFFFF},
                    new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        }
        edge(c, paint, r, radius, density * 0.5f, density);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private static void edge(Canvas c, Paint paint, RectF r, float radius, float inset,
            float width) {
        paint.setStrokeWidth(width);
        c.drawRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset,
                Math.max(0, radius - inset), Math.max(0, radius - inset), paint);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
