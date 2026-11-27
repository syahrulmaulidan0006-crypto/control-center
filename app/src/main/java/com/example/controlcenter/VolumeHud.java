package com.example.controlcenter;

import android.media.AudioManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/** Popup volume cairan di tepi kiri, muncul saat tombol volume ditekan di luar panel. */
class VolumeHud extends FrameLayout {
    private final OverlayService service;
    private final AudioManager audio;
    private final VerticalSlider slider;
    private boolean leaving, entered;
    private final Runnable timeout = this::leave;

    VolumeHud(OverlayService service) {
        super(service);
        this.service = service;
        audio = service.getSystemService(AudioManager.class);
        // Ruang di sekeliling untuk gerakan membesar saat disentuh.
        setPadding(dp(10), dp(10), dp(10), dp(10));
        setClipChildren(false);
        setClipToPadding(false);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        slider = new VerticalSlider(service, R.drawable.ic_volume, level(), value -> {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
            } catch (SecurityException ignored) { }
        }, null);
        slider.levelIcons(R.drawable.ic_volume0, R.drawable.ic_volume1);
        slider.announce = true;
        // Tembus ke layar, dengan selaput secukupnya agar angkanya terbaca.
        slider.backing = Glass.style != PopupGlass.FROSTED ? 0x59101420
                : BlurPane.supported(service) ? 0x80283248 : 0xD02C3448;
        slider.setContentDescription("Volume");
        addView(slider, new LayoutParams(dp(54), dp(190)));
        slider.setAlpha(0);
    }

    private BlurPane pane;

    /** Jendela buram di belakang popup; boleh null. */
    void behind(BlurPane under) {
        pane = under;
        if (pane != null) pane.follow(slider);
    }

    @Override protected void onDetachedFromWindow() {
        if (pane != null) pane.close();
        pane = null;
        super.onDetachedFromWindow();
    }

    static WindowManager.LayoutParams place(OverlayService service) {
        WindowManager.LayoutParams lp = service.params(-2, -2,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        lp.y = -Math.round(60 * service.getResources().getDisplayMetrics().density);
        return lp;
    }

    private float level() {
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                / (float) audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    void enter() {
        if (entered || leaving) return;
        entered = true;
        slider.setTranslationX(-dp(80));
        slider.setScaleY(0.85f);
        slider.animate().alpha(1).translationX(0).scaleY(1).setDuration(380)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
        update();
    }

    /** Volume baru saja berubah: cairan mengikuti dan popup bertahan lebih lama. */
    void update() {
        if (leaving) return;
        if (entered) slider.sync(level());
        stay();
    }

    private void stay() {
        removeCallbacks(timeout);
        postDelayed(timeout, 1900);
    }

    boolean leaving() { return leaving; }

    private void leave() {
        if (leaving) return;
        leaving = true;
        removeCallbacks(timeout);
        slider.animate().alpha(0).translationX(-dp(80)).scaleY(0.85f).setStartDelay(0)
                .setDuration(260).setInterpolator(new AccelerateInterpolator(1.3f)).start();
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> service.removeVolume(this), 280);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        stay();
        return super.dispatchTouchEvent(e);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
