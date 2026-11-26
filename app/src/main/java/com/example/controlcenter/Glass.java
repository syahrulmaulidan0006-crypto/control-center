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

    /** Gaya yang sedang dipakai; diperbarui setiap panel, popup, atau jendela dibuat. */
    static int style = PopupGlass.LIQUID;

    static void load(Context c) { style = PopupGlass.chosen(c); }

    static void draw(Canvas c, Paint paint, RectF r, float radius, float density, int tint) {
        boolean frosted = style == PopupGlass.FROSTED;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(tint);
        c.drawRoundRect(r, radius, radius, paint);
        if (frosted) {
            // Lapisan susu dan bintik halus membuat kaca terlihat buram.
            paint.setColor(0x14DCE4FF);
            c.drawRoundRect(r, radius, radius, paint);
        }
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, frosted
                ? new int[]{0x30FFFFFF, 0x16FFFFFF, 0x0CFFFFFF, 0x14FFFFFF}
                : new int[]{0x2EFFFFFF, 0x06FFFFFF, 0x02FFFFFF, 0x1CFFFFFF},
                new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, paint);
        if (frosted) {
            paint.setShader(new BitmapShader(PopupGlass.grain(), Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT));
            c.drawRoundRect(r, radius, radius, paint);
        }
        shine(c, paint, r, radius, density);
    }

    /** Pantulan di tepi kaca. */
    static void shine(Canvas c, Paint paint, RectF r, float radius, float density) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFFFFFFF);
        if (style == PopupGlass.FROSTED) {
            paint.setShader(null);
            paint.setColor(0x4DFFFFFF);
            edge(c, paint, r, radius, density * 0.5f, density);
        } else {
            // Lengkung tebal yang membias: lebarnya mengikuti ukuran benda.
            float lens = Math.min(Math.min(r.width(), r.height()) * 0.2f, density * 14);
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0x2BFFFFFF, 0x03FFFFFF, 0x03FFFFFF, 0x22FFFFFF},
                    new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            for (int i = 3; i >= 1; i--) {
                float width = lens * i / 3f;
                edge(c, paint, r, radius, width / 2, width);
            }
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0x6066E0FF, 0x0066E0FF, 0x00FF7AC8, 0x60FF7AC8},
                    new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            edge(c, paint, r, radius, density * 2f, density * 1.2f);
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0xE6FFFFFF, 0x24FFFFFF, 0x1AFFFFFF, 0xC7FFFFFF},
                    new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            edge(c, paint, r, radius, density * 0.7f, density * 1.4f);
        }
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
