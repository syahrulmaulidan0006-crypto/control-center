package com.example.controlcenter;

import android.service.notification.StatusBarNotification;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/** Popup notifikasi yang meluncur dari atas. */
class Banner extends FrameLayout {
    private final OverlayService service;
    private final StatusBarNotification note;
    private final NoteCard card;
    private boolean typing, leaving;
    private final Runnable timeout = this::leave;

    Banner(OverlayService service, StatusBarNotification note) {
        super(service);
        this.service = service;
        this.note = note;
        setPadding(dp(10), dp(6), dp(10), dp(10));
        card = new NoteCard(service, note, 0xDB1B2442);
        card.setOnClickListener(v -> { Notes.open(service, note); leave(); });
        card.gestures(this::leave, this::expand, this::leave);
        addView(card, new LayoutParams(-1, -2));
        card.actions(note, () -> {
            typing = true;
            removeCallbacks(timeout);
            postDelayed(timeout, 60000);
            service.bannerKeyboard(this);
        }, this::leave);
    }

    void enter() {
        card.setTranslationY(-dp(160));
        card.setScaleX(0.9f);
        card.animate().translationY(0).scaleX(1).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
        postDelayed(timeout, 6500);
    }

    void leave() {
        if (leaving) return;
        leaving = true;
        removeCallbacks(timeout);
        card.animate().translationY(-dp(220)).alpha(0).setDuration(220).start();
        // Dihapus dengan penjadwalan sendiri, tidak bergantung pada akhir animasi.
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> service.removeBanner(this), 260);
    }

    /** Jangan tutup otomatis selagi disentuh atau sedang mengetik. */
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        removeCallbacks(timeout);
        if (!leaving && e.getActionMasked() == MotionEvent.ACTION_UP) {
            postDelayed(timeout, typing ? 60000 : 5000);
        } else if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            postDelayed(timeout, typing ? 60000 : 5000);
        }
        return super.dispatchTouchEvent(e);
    }

    /** Tarik ke bawah untuk membaca pesan lengkap. */
    private void expand() { card.body.setMaxLines(8); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
