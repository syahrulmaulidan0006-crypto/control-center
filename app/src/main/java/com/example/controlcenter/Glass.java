package com.example.controlcenter;

import android.content.Context;
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

    Glass(Context context, int radiusDp, int tint) {
        density = context.getResources().getDisplayMetrics().density;
        radius = radiusDp * density;
        this.tint = tint;
    }

    @Override public void draw(Canvas c) {
        box.set(getBounds());
        draw(c, paint, box, Math.min(radius, Math.min(box.width(), box.height()) / 2),
                density, tint);
    }

    static void draw(Canvas c, Paint paint, RectF r, float radius, float density, int tint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(tint);
        c.drawRoundRect(r, radius, radius, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0x3DFFFFFF, 0x12FFFFFF, 0x08FFFFFF, 0x22FFFFFF},
                new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, paint);
        shine(c, paint, r, radius, density);
    }

    static void shine(Canvas c, Paint paint, RectF r, float radius, float density) {
        float half = density * 0.6f;
        RectF edge = new RectF(r.left + half, r.top + half, r.right - half, r.bottom - half);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density * 1.2f);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0xB3FFFFFF, 0x26FFFFFF, 0x14FFFFFF, 0x80FFFFFF},
                new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(edge, radius - half, radius - half, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
