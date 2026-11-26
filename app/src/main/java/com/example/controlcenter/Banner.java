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
        card = new NoteCard(service, note, 0x730E1430);
        card.setOnClickListener(v -> { Notes.open(service, note); leave(); });
        card.gestures(this::leave, this::expand, this::leave);
        addView(card, new LayoutParams(-1, -2));
        card.actions(note, () -> {
            typing = true;
            removeCallbacks(timeout);
            postDelayed(timeout, 60000);
            service.bannerKeyboard(this);
        }, this::leave);
        shade(card);
    }

    private void shade(android.view.View v) {
        if (v instanceof android.widget.TextView) {
            ((android.widget.TextView) v).setShadowLayer(dp(3), 0, dp(1), 0xB3000000);
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) shade(group.getChildAt(i));
        }
    }

    void enter() {
        card.setTranslationY(-dp(160));
        card.setScaleX(0.82f);
        card.setScaleY(0.82f);
        card.setAlpha(0);
        card.animate().translationY(0).scaleX(1).scaleY(1).alpha(1).setDuration(460)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
        postDelayed(timeout, 6500);
    }

    void leave() {
        if (leaving) return;
        leaving = true;
        removeCallbacks(timeout);
        card.animate().translationY(-dp(190)).scaleX(0.82f).scaleY(0.82f).alpha(0)
                .setStartDelay(0).setDuration(340)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f)).start();
        // Dihapus dengan penjadwalan sendiri, tidak bergantung pada akhir animasi.
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> service.removeBanner(this), 370);
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
    private void expand() {
        card.body.setMaxLines(8);
        card.body.setAlpha(0.2f);
        card.body.animate().alpha(1).setDuration(300).start();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
