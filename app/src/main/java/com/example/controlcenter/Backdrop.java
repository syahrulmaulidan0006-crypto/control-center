package com.example.controlcenter;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Latar halaman pengaturan: gelap dengan gumpalan warna lembut agar kaca terlihat. */
class Backdrop extends Drawable {
    /** {x, y, jari-jari} dalam bagian dari lebar dan tinggi, lalu warnanya. */
    private static final float[][] SPOTS = {
            {0.05f, 0.08f, 0.95f}, {1.0f, 0.34f, 0.85f}, {0.1f, 0.78f, 0.9f}, {0.9f, 1.0f, 0.7f}};
    private static final int[] COLORS = {0xB32F6FE0, 0x998B5CF6, 0x7319A7A0, 0x66FF7A59};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawColor(0xFF0B0F24);
        for (int i = 0; i < SPOTS.length; i++) {
            float x = b.width() * SPOTS[i][0], y = b.height() * SPOTS[i][1];
            float r = b.width() * SPOTS[i][2];
            paint.setShader(new RadialGradient(x, y, Math.max(1, r), COLORS[i],
                    COLORS[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, paint);
        }
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
