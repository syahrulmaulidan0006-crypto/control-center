package com.example.controlcenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import java.util.Random;

/** Latar popup. Dua gaya: liquid glass yang bening dan glassmorphism yang buram. */
class PopupGlass extends Drawable {
    static final String KEY = "popupStyle";
    static final int LIQUID = 0, FROSTED = 1;
    static final String[] NAMES = {"Liquid glass", "Glassmorphism"};
    private static Bitmap grain;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path clip = new Path();
    private final float d;
    private final int style, clear;

    /** Kebeningan 0 (pekat) sampai 100 (hampir bening). 50 adalah kepekatan asli tiap gaya. */
    static final String CLEAR = "popupClear";

    PopupGlass(Context context, int style) {
        this(context, style, clearness(context));
    }

    PopupGlass(Context context, int style, int clear) {
        d = context.getResources().getDisplayMetrics().density;
        this.style = style;
        this.clear = clear;
    }

    static int clearness(Context c) { return Prefs.of(c).getInt(CLEAR, 80); }

    /** Warna dasar dengan kepekatan sesuai pilihan kebeningan. */
    private int base(int color) {
        int usual = color >>> 24;
        int alpha = clear <= 50 ? 240 + (usual - 240) * clear / 50
                : usual + (8 - usual) * (clear - 50) / 50;
        return Theme.alpha(color, alpha);
    }

    static int chosen(Context c) { return Prefs.of(c).getInt(KEY, LIQUID); }

    @Override public void draw(Canvas c) {
        box.set(getBounds());
        Callback host = getCallback();
        if (host instanceof android.view.View) Frost.begin((android.view.View) host);
        if (Frost.active()) {
            float r = Math.min((style == FROSTED ? 22 : 34) * d, box.height() / 2);
            Frost.draw(c, box, r, d, clear / 100f);
            // Popup berisi tulisan, jadi diberi selaput agar tetap terbaca.
            fill(c, box, r, Theme.alpha(0xFF0C1228, Math.round(150 - 120 * clear / 100f)));
            if (style == FROSTED) fill(c, box, r, 0x1FDCE4FF);
            Frost.rim(c, box, r, d);
            Frost.end();
        } else if (style == FROSTED) {
            frosted(c);
        } else {
            liquid(c);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Bening seperti tetesan air: bagian tengah tipis, tepi tebal dan memantulkan cahaya. */
    private void liquid(Canvas c) {
        float r = Math.min(34 * d, box.height() / 2);
        fill(c, box, r, base(0x6B0C1228));
        gradient(c, box, r, false, new int[]{0x2EFFFFFF, 0x05FFFFFF, 0x00FFFFFF, 0x1FFFFFFF},
                new float[]{0f, 0.35f, 0.7f, 1f});

        c.save();
        clip.reset();
        clip.addRoundRect(box, r, r, Path.Direction.CW);
        c.clipPath(clip);
        // Cahaya yang membias di sepanjang tepi, memudar ke arah dalam.
        for (int i = 6; i >= 1; i--) {
            stroke(c, r, 0, i * 3f * d, new int[]{0x30FFFFFF, 0x04FFFFFF, 0x04FFFFFF,
                    0x24FFFFFF});
        }
        // Semburat warna tipis seperti pembiasan pada lensa.
        stroke(c, r, 2.2f * d, 1.5f * d, new int[]{0x7066E0FF, 0x0066E0FF, 0x00FF7AC8,
                0x70FF7AC8});
        c.restore();
        stroke(c, r, 0.8f * d, 1.6f * d, new int[]{0xF2FFFFFF, 0x30FFFFFF, 0x26FFFFFF,
                0xD9FFFFFF});
    }

    /** Kaca es: pekat lembut, berbintik halus, dengan garis tepi rata. */
    private void frosted(Canvas c) {
        float r = Math.min(22 * d, box.height() / 2);
        fill(c, box, r, base(0xD1394264));
        gradient(c, box, r, true, new int[]{0x38FFFFFF, 0x17FFFFFF, 0x0AFFFFFF, 0x14FFFFFF},
                new float[]{0f, 0.35f, 0.75f, 1f});
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRoundRect(box, r, r, paint);
        paint.setShader(null);
        stroke(c, r, 0.5f * d, d, new int[]{0x73FFFFFF, 0x59FFFFFF, 0x4DFFFFFF, 0x66FFFFFF});
    }

    static Bitmap grain() {
        if (grain != null) return grain;
        Random random = new Random(7);
        int[] dots = new int[96 * 96];
        for (int i = 0; i < dots.length; i++) dots[i] = random.nextInt(26) << 24 | 0xFFFFFF;
        grain = Bitmap.createBitmap(dots, 96, 96, Bitmap.Config.ARGB_8888);
        return grain;
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
                    Math.round((colors[i] >>> 24) * (1 - 0.6f * clear / 100f)));
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
