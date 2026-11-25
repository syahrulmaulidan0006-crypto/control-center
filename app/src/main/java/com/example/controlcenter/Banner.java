package com.example.controlcenter;

import android.app.Notification;
import android.graphics.Color;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Popup notifikasi yang meluncur dari atas. */
class Banner extends FrameLayout {
    private final OverlayService service;
    private final StatusBarNotification note;
    private final NoteCard card;
    private boolean expanded, leaving;
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
        View handle = new View(service);
        handle.setBackground(new Glass(service, 4, 0x99FFFFFF));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(5));
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(8);
        card.addView(handle, p);
    }

    void enter() {
        card.setTranslationY(-dp(160));
        card.setScaleX(0.9f);
        card.animate().translationY(0).scaleX(1).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
        postDelayed(timeout, 5000);
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

    /** Jangan tutup otomatis selagi disentuh. */
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        removeCallbacks(timeout);
        if (!expanded && !leaving && e.getActionMasked() == MotionEvent.ACTION_UP) {
            postDelayed(timeout, 4000);
        }
        return super.dispatchTouchEvent(e);
    }

    private void expand() {
        if (expanded) return;
        expanded = true;
        removeCallbacks(timeout);
        // Popup yang dibiarkan terbuka tetap ditutup agar tidak menahan popup berikutnya.
        postDelayed(timeout, 30000);
        card.removeViewAt(card.getChildCount() - 1);
        card.body.setMaxLines(8);
        Notification.Action reply = Notes.replyAction(note);
        if (reply != null && !Prefs.is(service, "hideContent", false)) {
            LinearLayout row = new LinearLayout(service);
            row.setGravity(Gravity.CENTER_VERTICAL);
            EditText input = new EditText(service);
            input.setHint("Tulis balasan");
            input.setHintTextColor(0x99FFFFFF);
            input.setTextColor(Color.WHITE);
            input.setTextSize(15);
            input.setMaxLines(3);
            input.setPadding(dp(16), dp(10), dp(16), dp(10));
            input.setBackground(new Glass(service, 24, 0x22FFFFFF));
            input.setOnClickListener(v -> keyboard(input));
            row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView send = new ImageView(service);
            send.setImageResource(R.drawable.ic_play);
            send.setColorFilter(Theme.onAccent(service));
            send.setContentDescription("Kirim");
            send.setPadding(dp(13), dp(13), dp(13), dp(13));
            send.setBackground(new Glass(service, 24, Theme.accent(service)));
            send.setOnClickListener(v -> {
                String message = input.getText().toString().trim();
                if (message.isEmpty()) return;
                boolean ok = Notes.reply(service, reply, message);
                Toast.makeText(service, ok ? "Terkirim" : "Gagal mengirim",
                        Toast.LENGTH_SHORT).show();
                if (ok) leave();
            });
            LinearLayout.LayoutParams s = new LinearLayout.LayoutParams(dp(48), dp(48));
            s.leftMargin = dp(8);
            row.addView(send, s);
            add(row);
        }
        LinearLayout buttons = new LinearLayout(service);
        Notification.Action read = Notes.readAction(note);
        button(buttons, read != null ? "Tandai dibaca" : "Hapus", () -> {
            if (read == null || !Notes.send(service, read.actionIntent, null)) {
                MusicListener.dismiss(note.getKey());
            }
            leave();
        });
        button(buttons, "Buka aplikasi", () -> { Notes.open(service, note); leave(); });
        button(buttons, "Tutup", this::leave);
        add(buttons);
    }

    private void keyboard(EditText input) {
        service.bannerKeyboard(this);
        input.postDelayed(() -> {
            input.requestFocus();
            service.getSystemService(InputMethodManager.class).showSoftInput(input, 0);
        }, 200);
    }

    private void add(android.view.View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        card.addView(v, p);
    }

    private void button(LinearLayout row, String label, Runnable click) {
        TextView t = new TextView(service);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setBackground(new Glass(service, 22, 0x1FFFFFFF));
        t.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(t, p);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
