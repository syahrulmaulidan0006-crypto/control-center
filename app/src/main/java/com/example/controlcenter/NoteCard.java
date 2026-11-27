package com.example.controlcenter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kartu notifikasi kaca yang bisa digeser. */
class NoteCard extends LinearLayout {
    private float downX, downY;
    private boolean swiping;
    boolean rightOnly;
    /** Bagian dari lebar kartu yang harus dilewati agar dianggap dibuang. */
    static final float FAR = 0.38f;
    /** Dipanggil tiap kartu berpindah ke samping. */
    Runnable shift;
    /** true selagi jari menggeser kartu, agar daftar tidak disusun ulang di tengahnya. */
    java.util.function.Consumer<Boolean> held;
    private VelocityTracker velocity;
    private Runnable dismiss, pullDown, swipeUp;
    final TextView body;
    private final LinearLayout words;

    NoteCard(Context context, StatusBarNotification n, int tint) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(14), dp(14), dp(14));
        setBackground(new Glass(context, 28, tint).reading());
        setClickable(true);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Drawable photo = Notes.photo(context, n);
        FrameLayout images = new FrameLayout(context);
        ImageView main = new ImageView(context);
        main.setScaleType(ImageView.ScaleType.CENTER_CROP);
        main.setImageDrawable(photo != null ? photo : Notes.icon(context, n));
        main.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(13));
            }
        });
        main.setClipToOutline(true);
        images.addView(main, new FrameLayout.LayoutParams(dp(46), dp(46)));
        if (photo != null) {
            ImageView badge = new ImageView(context);
            badge.setImageDrawable(Notes.icon(context, n));
            images.addView(badge, new FrameLayout.LayoutParams(dp(20), dp(20),
                    Gravity.BOTTOM | Gravity.END));
        }
        row.addView(images, new LayoutParams(dp(48), dp(48)));

        words = new LinearLayout(context);
        words.setOrientation(VERTICAL);
        words.setPadding(dp(12), 0, 0, 0);
        LinearLayout head = new LinearLayout(context);
        TextView app = line(Notes.app(context, n.getPackageName()), 12, 0xCCFFFFFF);
        head.addView(app, new LayoutParams(0, -2, 1));
        head.addView(line(Notes.age(n.getPostTime()), 12, 0xCCFFFFFF));
        words.addView(head);
        TextView title = line(Notes.title(n), 15, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        words.addView(title);
        body = line(Notes.body(context, n), 14, Color.WHITE);
        body.setSingleLine(false);
        body.setMaxLines(2);
        words.addView(body);
        row.addView(words, new LayoutParams(0, -2, 1));
        addView(row, new LayoutParams(-1, -2));
    }

    /** Keterangan kecil pada kartu teratas sebuah tumpukan. */
    void more(String value) {
        TextView t = line(value, 12, 0xB3FFFFFF);
        t.setPadding(0, dp(3), 0, 0);
        words.addView(t);
    }

    /**
     * Tombol Balas dan Tandai dibaca bila aplikasinya mendukung.
     * onType dipanggil saat kolom balasan dibuka, done setelah aksi berhasil.
     */
    void actions(StatusBarNotification n, Runnable onType, Runnable done) {
        Context context = getContext();
        android.app.Notification.Action reply = Prefs.is(context, "hideContent", false)
                ? null : Notes.replyAction(n);
        android.app.Notification.Action read = Notes.readAction(n);
        if (reply == null && read == null) return;
        LinearLayout buttons = new LinearLayout(context);
        LayoutParams top = new LayoutParams(-1, -2);
        top.topMargin = dp(10);
        if (reply != null) {
            button(buttons, "Balas", () -> {
                buttons.setVisibility(GONE);
                LinearLayout field = replyRow(n, reply, done);
                addView(field, indexOfChild(buttons) + 1, top);
                Motion.enter(field, 0, dp(14));
                onType.run();
                View row = getChildAt(indexOfChild(buttons) + 1);
                View input = ((LinearLayout) row).getChildAt(0);
                input.postDelayed(() -> {
                    input.requestFocus();
                    context.getSystemService(android.view.inputmethod.InputMethodManager.class)
                            .showSoftInput(input, 0);
                }, 250);
            });
        }
        if (read != null) {
            button(buttons, "Tandai dibaca", () -> {
                if (Notes.send(context, read.actionIntent, null)) {
                    MusicListener.dismiss(n.getKey());
                    done.run();
                } else {
                    toast("Gagal menandai dibaca");
                }
            });
        }
        addView(buttons, top);
    }

    private LinearLayout replyRow(StatusBarNotification n,
            android.app.Notification.Action reply, Runnable done) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.EditText input = new android.widget.EditText(context);
        input.setHint("Tulis balasan");
        input.setHintTextColor(0x99FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(15);
        input.setMaxLines(3);
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setBackground(new Glass(context, 24, 0x22FFFFFF));
        row.addView(input, new LayoutParams(0, -2, 1));
        ImageView send = new ImageView(context);
        send.setImageResource(R.drawable.ic_send);
        send.setColorFilter(Theme.onAccent(context));
        send.setContentDescription("Kirim");
        send.setPadding(dp(13), dp(13), dp(13), dp(13));
        send.setBackground(new Glass(context, 24, Theme.accent(context)));
        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            boolean ok = Notes.reply(context, reply, message);
            toast(ok ? "Terkirim" : "Gagal mengirim");
            if (ok) done.run();
        });
        Motion.press(send);
        LayoutParams p = new LayoutParams(dp(48), dp(48));
        p.leftMargin = dp(8);
        row.addView(send, p);
        return row;
    }

    private void button(LinearLayout row, String label, Runnable click) {
        TextView t = line(label, 13, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(new Glass(getContext(), 22, 0x26FFFFFF));
        t.setOnClickListener(v -> click.run());
        Motion.press(t);
        LayoutParams p = new LayoutParams(0, dp(42), 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(t, p);
    }

    private void toast(String message) {
        android.widget.Toast.makeText(getContext(), message,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    void gestures(Runnable dismiss, Runnable pullDown, Runnable swipeUp) {
        this.dismiss = dismiss;
        this.pullDown = pullDown;
        this.swipeUp = swipeUp;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        track(e);
        return swiping;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        track(e);
        int a = e.getActionMasked();
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        if (a == MotionEvent.ACTION_MOVE && swiping) {
            if (rightOnly) dx = Math.max(0, dx);
            setTranslationX(dx);
            setAlpha(1 - Math.min(0.5f, Math.abs(dx) / Math.max(1, getWidth()) * 0.7f));
            if (shift != null) shift.run();
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (swiping) {
                velocity.computeCurrentVelocity(1000);
                if (rightOnly) dx = Math.max(0, dx);
                float speed = velocity.getXVelocity();
                // Jentikan cepat membuang kartu hanya bila arahnya sama dengan geseran.
                boolean flick = Math.abs(speed) > dp(900) && speed * dx > 0;
                boolean back = Math.abs(speed) > dp(500) && speed * dx < 0;
                boolean gone = a == MotionEvent.ACTION_UP && dx != 0 && !back
                        && (Math.abs(dx) > getWidth() * FAR || flick);
                swiping = false;
                if (held != null) held.accept(false);
                if (gone && dismiss != null) {
                    fly(Math.signum(dx), Math.abs(speed));
                } else {
                    animate().translationX(0).alpha(1).setStartDelay(0).setDuration(340)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                            .setUpdateListener(x -> { if (shift != null) shift.run(); }).start();
                }
                return true;
            }
            if (a == MotionEvent.ACTION_UP && dy > dp(24) && pullDown != null) {
                pullDown.run();
                return true;
            }
            if (a == MotionEvent.ACTION_UP && dy < -dp(24) && swipeUp != null) {
                swipeUp.run();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    /** Kartu terbang keluar secepat jentikan jari, baru kemudian dibuang. */
    private void fly(float way, float speed) {
        float rest = getWidth() * 1.15f - Math.abs(getTranslationX());
        long millis = speed > dp(900)
                ? (long) Math.max(160, Math.min(300, rest * 1700 / speed)) : 280;
        animate().translationX(way * getWidth() * 1.15f).alpha(0).scaleX(0.9f).scaleY(0.9f)
                .setStartDelay(0).setDuration(millis)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                .setUpdateListener(x -> { if (shift != null) shift.run(); })
                .withEndAction(dismiss).start();
    }

    /** Kartu sedikit mengecil selagi disentuh. */
    private void touchFeel(int action) {
        float scale = action == MotionEvent.ACTION_DOWN ? 0.975f : 1;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            animate().setUpdateListener(null);
            animate().scaleX(scale).scaleY(scale).setStartDelay(0).setDuration(160).start();
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        touchFeel(e.getActionMasked());
        return super.dispatchTouchEvent(e);
    }

    private void track(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getRawX();
            downY = e.getRawY();
            swiping = false;
            if (velocity == null) velocity = VelocityTracker.obtain();
            velocity.clear();
        }
        if (velocity == null) return;
        MotionEvent raw = MotionEvent.obtain(e);
        raw.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(raw);
        raw.recycle();
        if (!swiping && dismiss != null && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(e.getRawX() - downX), dy = Math.abs(e.getRawY() - downY);
            // Di pusat notifikasi, geser ke kiri dipakai untuk pindah halaman.
            if (rightOnly && e.getRawX() < downX) return;
            if (dx > ViewConfiguration.get(getContext()).getScaledTouchSlop() && dx > dy * 1.3f) {
                swiping = true;
                if (held != null) held.accept(true);
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
    }

    private TextView line(String value, int size, int color) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setShadowLayer(dp(3), 0, dp(1), 0xB3000000);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
