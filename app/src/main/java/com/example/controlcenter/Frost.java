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
 * Kaca buram asli: layar dipotret sesaat sebelum jendela kita tampil, diburamkan, lalu tiap
 * benda kaca menggambar bagian yang tepat berada di belakangnya. Potretnya hanya disimpan di
 * memori. Memerlukan Android 11; di bawahnya kaca memakai gambar biasa.
 */
final class Frost {
    /** Lebar gambar buram dibanding layar asli. */
    private static final int SHRINK = 8;
    private static final Executor worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static Bitmap picture;
    private static int screenWidth, screenHeight, generation;

    /** Benda yang sedang digambar. */
    private static final Matrix toBitmap = new Matrix();
    private static boolean active;

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
        android.util.DisplayMetrics screen = new android.util.DisplayMetrics();
        service.getSystemService(android.hardware.display.DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(screen);
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, worker,
                    new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    Bitmap made = null;
                    try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                        Bitmap hard = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        Bitmap full = hard.copy(Bitmap.Config.ARGB_8888, false);
                        hard.recycle();
                        made = soften(full, Math.round(5 - 2.5f * clear));
                        full.recycle();
                    } catch (RuntimeException | OutOfMemoryError e) {
                        android.util.Log.w("ControlCenter", "potret gagal diolah: " + e);
                    }
                    Bitmap ready = made;
                    main.post(() -> {
                        if (ready != null) {
                            picture = ready;
                            screenWidth = screen.widthPixels;
                            screenHeight = screen.heightPixels;
                            generation++;
                            android.util.Log.i("ControlCenter", "kaca buram siap");
                        }
                        if (done != null) done.run();
                    });
                }

                @Override public void onFailure(int code) {
                    // Terlalu rapat dengan potret sebelumnya: gambar lama masih dipakai.
                    android.util.Log.i("ControlCenter", "potret ditolak: " + code);
                    main.post(() -> { if (done != null) done.run(); });
                }
            });
        } catch (RuntimeException e) {
            android.util.Log.w("ControlCenter", "potret tidak tersedia: " + e);
            if (done != null) done.run();
        }
    }

    /** Diperkecil, diburamkan, dan warnanya dibuat lebih hidup. */
    private static Bitmap soften(Bitmap full, int radius) {
        // Pengecilan bertahap agar tidak bergerigi.
        Bitmap half = Bitmap.createScaledBitmap(full, full.getWidth() / 2,
                full.getHeight() / 2, true);
        Bitmap quarter = Bitmap.createScaledBitmap(half, half.getWidth() / 2,
                half.getHeight() / 2, true);
        int w = Math.max(1, full.getWidth() / SHRINK), h = Math.max(1, full.getHeight() / SHRINK);
        Bitmap small = Bitmap.createScaledBitmap(quarter, w, h, true);
        if (half != full) half.recycle();
        if (quarter != small) quarter.recycle();

        int[] from = new int[w * h], to = new int[w * h];
        small.getPixels(from, 0, w, 0, 0, w, h);
        // Tiga kali rata-rata kotak mendekati buram Gauss.
        for (int pass = 0; pass < 3; pass++) {
            box(from, to, w, h, radius, true);
            box(to, from, w, h, radius, false);
        }
        Bitmap blurred = Bitmap.createBitmap(from, w, h, Bitmap.Config.ARGB_8888);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ColorMatrix vivid = new ColorMatrix();
        vivid.setSaturation(1.55f);
        Paint colors = new Paint();
        colors.setColorFilter(new ColorMatrixColorFilter(vivid));
        new Canvas(out).drawBitmap(blurred, 0, 0, colors);
        blurred.recycle();
        return out;
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

    static boolean ready() { return picture != null; }

    /** Hanya jendela yang memintanya yang memakai kaca buram (panel, popup, volume). */
    static void enable(View window) { window.setTag(R.id.frost, Boolean.TRUE); }

    /** Dipanggil di awal penggambaran sebuah benda; selalu diakhiri dengan end(). */
    static void begin(View view) {
        active = false;
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
    }

    static void end() { active = false; }

    static boolean active() { return active; }

    /** Isi kaca: latar buram, bias di tepi, lalu pantulan tipis. */
    static void draw(Canvas c, RectF r, float radius, float density, float clear) {
        BitmapShader shader = new BitmapShader(picture, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        shader.setLocalMatrix(toBitmap);
        paint.setStyle(Paint.Style.FILL);
        paint.setColorFilter(null);
        paint.setShader(shader);
        c.drawRoundRect(r, radius, radius, paint);

        // Tepi seperti lensa: gambar dari luar bentuk terlipat masuk ke pinggiran.
        float lens = Math.min(Math.min(r.width(), r.height()) * 0.16f, 16 * density);
        Matrix bent = new Matrix(toBitmap);
        bent.postScale(Math.max(0.5f, 1 - 2.2f * lens / Math.max(1, r.width())),
                Math.max(0.5f, 1 - 2.2f * lens / Math.max(1, r.height())),
                r.centerX(), r.centerY());
        BitmapShader rim = new BitmapShader(picture, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        rim.setLocalMatrix(bent);
        paint.setStyle(Paint.Style.STROKE);
        paint.setShader(rim);
        for (int i = 3; i >= 1; i--) {
            float width = lens * i / 3f, inset = width / 2;
            paint.setStrokeWidth(width);
            paint.setAlpha(90);
            c.drawRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset,
                    Math.max(0, radius - inset), Math.max(0, radius - inset), paint);
        }
        paint.setAlpha(255);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        // Selaput gelap tipis menjaga ikon putih tetap terbaca di latar terang.
        light.setShader(null);
        light.setStyle(Paint.Style.FILL);
        light.setColor(Theme.alpha(0xFF101418, Math.round(78 - 44 * clear)));
        c.drawRoundRect(r, radius, radius, light);
    }

    /** Garis cahaya tipis di tepi: terang di kiri atas dan kanan bawah. */
    static void rim(Canvas c, RectF r, float radius, float density) {
        light.setStyle(Paint.Style.STROKE);
        light.setColor(0xFFFFFFFF);
        light.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0x24FFFFFF, 0x05FFFFFF, 0x03FFFFFF, 0x1AFFFFFF},
                new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        for (int i = 3; i >= 1; i--) {
            float width = i * 2.2f * density, inset = width / 2;
            light.setStrokeWidth(width);
            c.drawRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset,
                    Math.max(0, radius - inset), Math.max(0, radius - inset), light);
        }
        light.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0xCCFFFFFF, 0x2EFFFFFF, 0x24FFFFFF, 0x99FFFFFF},
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
