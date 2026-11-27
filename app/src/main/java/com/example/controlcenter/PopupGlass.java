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

/** Latar popup. Dua gaya: liquid glass yang bening dan glassmorphism yang buram. */
class PopupGlass extends Drawable {
    static final String KEY = "popupStyle";
    static final int LIQUID = 0, FROSTED = 1;
    static final String[] NAMES = {"Liquid glass", "Glassmorphism"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float d;
    private final int style, clear;
    private final boolean blurred;

    /** Kebeningan 0 (pekat) sampai 100 (hampir bening). 50 adalah kepekatan asli tiap gaya. */
    static final String CLEAR = "popupClear";

    PopupGlass(Context context, int style) {
        this(context, style, clearness(context));
    }

    PopupGlass(Context context, int style, int clear) {
        d = context.getResources().getDisplayMetrics().density;
        this.style = style;
        this.clear = clear;
        blurred = BlurPane.supported(context);
    }

    static int clearness(Context c) { return Prefs.of(c).getInt(CLEAR, 80); }

    static int chosen(Context c) { return Prefs.of(c).getInt(KEY, LIQUID); }

    @Override public void draw(Canvas c) {
        box.set(getBounds());
        // Popup tembus langsung ke layar di belakangnya, bukan ke potret lama.
        if (style == FROSTED) frosted(c);
        else liquid(c);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Bening: selaput tipis secukupnya agar tulisan terbaca, dengan tepi berkilau. */
    private void liquid(Canvas c) {
        float r = Math.min(34 * d, box.height() / 2);
        fill(c, box, r, Theme.alpha(0xFF0A1020, Math.min(215, 60 + (100 - clear) * 3 / 2)));
        gradient(c, box, r, false, new int[]{0x24FFFFFF, 0x05FFFFFF, 0x00FFFFFF, 0x14FFFFFF},
                new float[]{0f, 0.35f, 0.7f, 1f});
        stroke(c, r, 0.5f * d, d, new int[]{0xB3FFFFFF, 0x26FFFFFF, 0x1FFFFFFF, 0x80FFFFFF});
    }

    /**
     * Kaca es. Bila HP bisa memburamkan di belakangnya cukup selaput sedang; bila tidak,
     * selaputnya pekat agar tulisan di belakang tidak menembus jelas.
     */
    private void frosted(Canvas c) {
        float r = Math.min(26 * d, box.height() / 2);
        int alpha = (blurred ? 128 : 218) + (80 - clear) * 3 / 5;
        fill(c, box, r, Theme.alpha(0xFF2E3850, Math.max(blurred ? 90 : 190,
                Math.min(240, alpha))));
        gradient(c, box, r, true, new int[]{0x33FFFFFF, 0x22FFFFFF, 0x1AFFFFFF, 0x22FFFFFF},
                new float[]{0f, 0.35f, 0.75f, 1f});
        stroke(c, r, 0.5f * d, d, new int[]{0x66FFFFFF, 0x4DFFFFFF, 0x40FFFFFF, 0x59FFFFFF});
    }

    private void fill(Canvas c, RectF area, float r, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        c.drawRoundRect(area, r, r, paint);
    }

    private void gradient(Canvas c, RectF area, float r, boolean diagonal, int[] colors,
            float[] stops) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < colors.length; i++) {
            colors[i] = Theme.alpha(colors[i],
                    Math.round((colors[i] >>> 24) * (1 - 0.4f * clear / 100f)));
        }
        paint.setShader(new LinearGradient(area.left, area.top,
                diagonal ? area.right : area.left, area.bottom, colors, stops,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(area, r, r, paint);
        paint.setShader(null);
    }

    /** Garis tepi dengan gradasi dari kiri atas ke kanan bawah. */
    private void stroke(Canvas c, float r, float inset, float width, int[] colors) {
        RectF edge = new RectF(box);
        edge.inset(inset, inset);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(0xFFFFFFFF);
        // Arah gradasi dibuat pendek agar kilau tetap di sudut pada kartu yang lebar.
        paint.setShader(new LinearGradient(box.left, box.top,
                box.left + box.width() * 0.35f, box.bottom + box.height() * 0.2f,
                new int[]{colors[0], colors[1]}, null, Shader.TileMode.CLAMP));
        c.save();
        c.clipRect(box.left, box.top, box.centerX(), box.bottom);
        c.drawRoundRect(edge, r - inset, r - inset, paint);
        c.restore();
        paint.setShader(new LinearGradient(box.right - box.width() * 0.35f,
                box.top - box.height() * 0.2f, box.right, box.bottom,
                new int[]{colors[2], colors[3]}, null, Shader.TileMode.CLAMP));
        c.save();
        c.clipRect(box.centerX(), box.top, box.right, box.bottom);
        c.drawRoundRect(edge, r - inset, r - inset, paint);
        c.restore();
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
