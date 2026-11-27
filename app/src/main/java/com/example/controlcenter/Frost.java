package com.example.controlcenter;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Kaca bening asli: layar dipotret sesaat sebelum jendela kita tampil, lalu tiap benda kaca
 * menggambar bagian yang tepat berada di belakangnya. Liquid glass hampir tidak diburamkan dan
 * membiaskan gambar di pinggirnya; glassmorphism diburamkan lembut dengan permukaan rata.
 * Permukaan berisi tulisan memakai gambar yang sedikit lebih buram agar tetap terbaca.
 * Potretnya hanya disimpan di memori. Memerlukan Android 11.
 */
final class Frost {
    private static final Executor worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    /** Untuk tombol (paling bening) dan untuk permukaan berisi tulisan. */
    private static Bitmap picture, soft;
    /** Selaput gelap tambahan (0..255) bila layar di belakang sangat terang. */
    private static int veil;
    private static boolean failed;
    private static int screenWidth, screenHeight, generation;

    /** Benda yang sedang digambar. */
    private static final Matrix toBitmap = new Matrix();
    private static boolean active, inner;

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Map<View, Follower> followed = new WeakHashMap<>();

    /** Memotret layar; done selalu dipanggil, berhasil atau tidak. */
    static void capture(AccessibilityService service, Runnable done) {
        if (Build.VERSION.SDK_INT < 30 || !Prefs.is(service, "frost", true)) {
            if (done != null) done.run();
            return;
        }
        float clear = PopupGlass.clearness(service) / 100f;
        boolean liquid = PopupGlass.chosen(service) == PopupGlass.LIQUID;
        android.util.DisplayMetrics screen = new android.util.DisplayMetrics();
        service.getSystemService(android.hardware.display.DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(screen);
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, worker,
                    new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    Bitmap clearer = null, reading = null;
                    int shade = 0;
                    try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                        Bitmap hard = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        Bitmap full = hard.copy(Bitmap.Config.ARGB_8888, false);
                        hard.recycle();
                        float haze = 1 - clear;
                        clearer = soften(full, liquid ? 3 : 6,
                                1 + Math.round((liquid ? 2 : 3) * haze));
                        reading = soften(full, liquid ? 6 : 8,
                                2 + Math.round((liquid ? 3 : 4) * haze));
                        full.recycle();
                        shade = shade(reading);
                    } catch (RuntimeException | OutOfMemoryError e) {
                        android.util.Log.w("ControlCenter", "potret gagal diolah: " + e);
                    }
                    Bitmap first = clearer, second = reading;
                    int darker = shade;
                    main.post(() -> {
                        if (first != null && second != null) {
                            picture = first;
                            soft = second;
                            veil = darker;
                            failed = false;
                            screenWidth = screen.widthPixels;
                            screenHeight = screen.heightPixels;
                            generation++;
                            android.util.Log.i("ControlCenter", "kaca bening siap");
                        } else {
                            failed = picture == null;
                        }
                        if (done != null) done.run();
                    });
                }

                @Override public void onFailure(int code) {
                    // Terlalu rapat dengan potret sebelumnya: gambar lama masih dipakai.
                    android.util.Log.i("ControlCenter", "potret ditolak: " + code);
                    main.post(() -> {
                        failed = picture == null;
                        if (done != null) done.run();
                    });
                }
            });
        } catch (RuntimeException e) {
            android.util.Log.w("ControlCenter", "potret tidak tersedia: " + e);
            failed = picture == null;
            if (done != null) done.run();
        }
    }

    /** Diperkecil, diburamkan seperlunya, dan warnanya sedikit dihidupkan. */
    private static Bitmap soften(Bitmap full, int shrink, int radius) {
        int w = Math.max(1, full.getWidth() / shrink), h = Math.max(1, full.getHeight() / shrink);
        // Pengecilan bertahap agar tidak bergerigi.
        Bitmap small = full;
        while (small.getWidth() / 2 > w) {
            Bitmap next = Bitmap.createScaledBitmap(small, small.getWidth() / 2,
                    small.getHeight() / 2, true);
            if (small != full) small.recycle();
            small = next;
        }
        Bitmap sized = Bitmap.createScaledBitmap(small, w, h, true);
        if (small != full && small != sized) small.recycle();

        int[] from = new int[w * h], to = new int[w * h];
        sized.getPixels(from, 0, w, 0, 0, w, h);
        if (sized != full) sized.recycle();
        // Tiga kali rata-rata kotak mendekati buram Gauss.
        for (int pass = 0; pass < 3; pass++) {
            box(from, to, w, h, radius, true);
            box(to, from, w, h, radius, false);
        }
        Bitmap blurred = Bitmap.createBitmap(from, w, h, Bitmap.Config.ARGB_8888);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ColorMatrix vivid = new ColorMatrix();
        vivid.setSaturation(1.25f);
        Paint colors = new Paint();
        colors.setColorFilter(new ColorMatrixColorFilter(vivid));
        new Canvas(out).drawBitmap(blurred, 0, 0, colors);
        blurred.recycle();
        return out;
    }

    /** Ikon dan tulisan putih perlu latar sedikit digelapkan bila layarnya terang. */
    private static int shade(Bitmap small) {
        Bitmap tiny = Bitmap.createScaledBitmap(small, 16, 32, true);
        float sum = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 16; x++) sum += android.graphics.Color.luminance(tiny.getPixel(x, y));
        }
        if (tiny != small) tiny.recycle();
        // Luminance bernilai linear; 0,25 kira-kira abu-abu sedang.
        return Math.round(255 * Math.max(0, Math.min(0.3f, (sum / 512 - 0.25f) * 0.6f)));
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean across) {
        int lines = across ? h : w, length = across ? w : h;
        int step = across ? 1 : w, next = across ? w : 1, size = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int start = line * next, red = 0, green = 0, blue = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[start + Math.max(0, Math.min(length - 1, i)) * step];
                red += p >> 16 & 255;
                green += p >> 8 & 255;
                blue += p & 255;
            }
            for (int i = 0; i < length; i++) {
                out[start + i * step] = 0xFF000000 | red / size << 16 | green / size << 8
                        | blue / size;
                int add = in[start + Math.min(length - 1, i + r + 1) * step];
                int drop = in[start + Math.max(0, i - r) * step];
                red += (add >> 16 & 255) - (drop >> 16 & 255);
                green += (add >> 8 & 255) - (drop >> 8 & 255);
                blue += (add & 255) - (drop & 255);
            }
        }
    }

    /**
     * Kegelapan (0..150) yang sama untuk bagian di dalam dan di luar kaca, supaya keduanya
     * menyambung: lebih gelap bila layarnya terang atau kaca dibuat pekat.
     */
    static int dark(float clear) { return Math.min(150, veil + Math.round(16 + 100 * (1 - clear))); }

    /** Benar bila potret tidak bisa dipakai sama sekali di HP ini. */
    static boolean failed() { return failed; }

    /** Benda berikutnya berada di atas kaca lain, jadi cukup diberi isian tipis. */
    static void inner(boolean value) { inner = value; }

    static boolean isInner() { return inner; }

    /** Hanya jendela yang memintanya yang memakai kaca buram (panel, popup, volume). */
    static void enable(View window) { window.setTag(R.id.frost, Boolean.TRUE); }

    /** Dipanggil di awal penggambaran sebuah benda; selalu diakhiri dengan end(). */
    static void begin(View view) {
        active = false;
        inner = false;
        if (view == null || view.getRootView().getTag(R.id.frost) == null) return;
        follow(view);
        if (picture == null) return;
        Matrix place = new Matrix();
        view.transformMatrixToGlobal(place);
        // Potret dari arah layar lain tidak dipakai.
        android.util.DisplayMetrics now = view.getResources().getDisplayMetrics();
        if (now.widthPixels > now.heightPixels != screenWidth > screenHeight) return;
        if (!place.invert(toBitmap)) return;
        toBitmap.preScale(screenWidth / (float) picture.getWidth(),
                screenHeight / (float) picture.getHeight());
        active = true;
        // Kaca di atas kaca akan terlihat seperti piringan tempelan.
        for (android.view.ViewParent p = view.getParent(); p instanceof View;
                p = p.getParent()) {
            android.graphics.drawable.Drawable under = ((View) p).getBackground();
            if (under instanceof Glass || under instanceof PopupGlass) {
                inner = true;
                break;
            }
        }
    }

    static void end() { active = false; }

    static boolean active() { return active; }

    // Jaring pembiasan: beberapa cincin titik dari tepi ke dalam. Isinya menyatu tanpa garis.
    private static final float[] DEPTH = {0, 0.06f, 0.13f, 0.22f, 0.33f, 0.46f, 0.62f, 0.8f, 1};
    private static final int ARC = 14, AROUND = ARC * 4;
    private static final float[] points = new float[(DEPTH.length * AROUND + 1) * 2];
    private static final float[] samples = new float[points.length];
    private static final short[] triangles =
            new short[(DEPTH.length - 1) * AROUND * 6 + AROUND * 3];

    static {
        int n = 0;
        for (int ring = 0; ring < DEPTH.length - 1; ring++) {
            for (int i = 0; i < AROUND; i++) {
                int a = ring * AROUND + i, b = ring * AROUND + (i + 1) % AROUND;
                int c = a + AROUND, d = b + AROUND;
                for (int v : new int[]{a, b, c, b, d, c}) triangles[n++] = (short) v;
            }
        }
        int last = (DEPTH.length - 1) * AROUND, middle = DEPTH.length * AROUND;
        for (int i = 0; i < AROUND; i++) {
            triangles[n++] = (short) (last + i);
            triangles[n++] = (short) (last + (i + 1) % AROUND);
            triangles[n++] = (short) middle;
        }
    }

    /**
     * Isi kaca. Liquid glass membelokkan gambar di pinggir seperti lensa cembung; glassmorphism
     * rata. reading dipakai permukaan berisi tulisan.
     */
    static void draw(Canvas c, RectF r, float radius, float density, float clear,
            boolean reading) {
        boolean liquid = Glass.style == PopupGlass.LIQUID;
        Bitmap source = reading ? soft : picture;
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        Matrix place = new Matrix(toBitmap);
        // toBitmap dibuat untuk gambar bening; gambar lain disesuaikan ukurannya.
        place.preScale(picture.getWidth() / (float) source.getWidth(),
                picture.getHeight() / (float) source.getHeight());
        shader.setLocalMatrix(place);
        paint.setStyle(Paint.Style.FILL);
        paint.setColorFilter(null);
        paint.setShader(shader);
        c.drawRoundRect(r, radius, radius, paint);
        if (liquid) refract(c, r, radius, density);
        paint.setShader(null);

        light.setShader(null);
        light.setStyle(Paint.Style.FILL);
        // Di dalam kaca hanya sedikit lebih terang daripada sekelilingnya.
        light.setColor(Theme.alpha(0xFF000000, dark(clear) + (reading ? 18 : 0)));
        c.drawRoundRect(r, radius, radius, light);
        light.setColor(Theme.alpha(0xFFFFFFFF, liquid ? 14 : 28));
        c.drawRoundRect(r, radius, radius, light);
    }

    private static void refract(Canvas c, RectF r, float radius, float density) {
        float small = Math.min(r.width(), r.height());
        float bezel = Math.min(small * 0.34f, 24 * density);
        if (bezel < 3 * density) return;
        float bend = bezel * 0.42f;
        int n = 0;
        for (float depth : DEPTH) {
            // Satu piksel ke dalam agar tepi halus dari gambar dasar tetap terlihat.
            float inset = 1 + depth * (bezel - 1);
            float corner = Math.max(0, Math.min(radius, small / 2) - inset);
            float away = 1 - depth;
            // Pembiasan memudar tepat di tepi, memuncak sedikit di dalamnya, lalu hilang.
            float shift = bend * (float) Math.pow(away, 2.2) * Math.min(1, 0.55f + depth * 6);
            for (int q = 0; q < 4; q++) {
                float cx = q == 0 || q == 3 ? r.right - inset - corner : r.left + inset + corner;
                float cy = q < 2 ? r.bottom - inset - corner : r.top + inset + corner;
                for (int i = 0; i < ARC; i++) {
                    double angle = Math.PI / 2 * (q + i / (double) (ARC - 1));
                    float nx = (float) Math.cos(angle), ny = (float) Math.sin(angle);
                    points[n] = cx + nx * corner;
                    points[n + 1] = cy + ny * corner;
                    samples[n] = points[n] - nx * shift;
                    samples[n + 1] = points[n + 1] - ny * shift;
                    n += 2;
                }
            }
        }
        points[n] = samples[n] = r.centerX();
        points[n + 1] = samples[n + 1] = r.centerY();
        c.drawVertices(Canvas.VertexMode.TRIANGLES, points.length, points, 0, samples, 0,
                null, 0, triangles, 0, triangles.length, paint);
    }

    /** Satu garis tipis di tepi: terang di kiri atas dan kanan bawah. */
    static void rim(Canvas c, RectF r, float radius, float density) {
        boolean liquid = Glass.style == PopupGlass.LIQUID;
        light.setStyle(Paint.Style.STROKE);
        light.setColor(0xFFFFFFFF);
        light.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, liquid
                ? new int[]{0xB3FFFFFF, 0x26FFFFFF, 0x1FFFFFFF, 0x73FFFFFF}
                : new int[]{0x66FFFFFF, 0x40FFFFFF, 0x38FFFFFF, 0x52FFFFFF},
                new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        light.setStrokeWidth(density);
        float inset = density * 0.5f;
        c.drawRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset,
                Math.max(0, radius - inset), Math.max(0, radius - inset), light);
        light.setShader(null);
        light.setStyle(Paint.Style.FILL);
    }

    private static void follow(View view) {
        if (followed.containsKey(view) || !view.isAttachedToWindow()) return;
        Follower f = new Follower(view);
        followed.put(view, f);
        view.getViewTreeObserver().addOnPreDrawListener(f);
        view.addOnAttachStateChangeListener(f);
    }

    /** Menggambar ulang benda kaca saat ia berpindah, agar latarnya tetap diam di tempat. */
    private static class Follower implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        private final java.lang.ref.WeakReference<View> target;
        private final float[] last = new float[9], now = new float[9];
        private final Matrix place = new Matrix();
        private int seen = -1;

        Follower(View view) { target = new java.lang.ref.WeakReference<>(view); }

        @Override public boolean onPreDraw() {
            View view = target.get();
            if (view == null || !view.isShown()) return true;
            place.reset();
            view.transformMatrixToGlobal(place);
            place.getValues(now);
            boolean moved = seen != generation;
            for (int i = 0; i < 9 && !moved; i++) moved = Math.abs(now[i] - last[i]) > 0.4f
                    || i < 2 && Math.abs(now[i] - last[i]) > 0.002f;
            if (moved) {
                seen = generation;
                System.arraycopy(now, 0, last, 0, 9);
                view.invalidate();
            }
            return true;
        }

        @Override public void onViewAttachedToWindow(View v) { }

        @Override public void onViewDetachedFromWindow(View v) {
            v.getViewTreeObserver().removeOnPreDrawListener(this);
            v.removeOnAttachStateChangeListener(this);
            followed.remove(v);
        }
    }
}
