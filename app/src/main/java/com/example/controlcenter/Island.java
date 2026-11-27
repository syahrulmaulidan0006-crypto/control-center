package com.example.controlcenter;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kapsul melayang di atas layar untuk hitung mundur, lagu, dan pengisian daya. */
class Island {
    static final String ON = "island", TOP = "islandY", TIMER_END = "timerEnd";
    private static final int NONE = 0, TIMER = 1, MUSIC = 2, CHARGE = 3;
    private static final int BLACK = 0xF2080A10, GREEN = 0xFF37D98A, AMBER = 0xFFFFB43C;

    private final OverlayService service;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Window window;
    private boolean expanded, running;
    private long chargeUntil;

    // Isi yang sedang ditampilkan.
    private int kind = NONE;
    private long timerEnd;
    private int level;
    private MediaController player;
    private Bitmap cover;
    private String title = "", artist = "";
    private boolean playing;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            update();
            if (running) handler.postDelayed(this, 1000);
        }
    };
    private final Runnable autoCollapse = () -> expand(false);

    Island(OverlayService service) {
        this.service = service;
        wm = service.getSystemService(WindowManager.class);
    }

    void start() {
        running = true;
        refresh();
    }

    /** Periksa keadaan sekarang juga. */
    void refresh() {
        handler.removeCallbacks(poll);
        if (running) handler.post(poll);
    }

    /** Hapus tanpa animasi, misalnya karena posisinya diubah di pengaturan. */
    void reset() {
        remove(false);
        refresh();
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        remove(false);
    }

    void charging() {
        chargeUntil = SystemClock.elapsedRealtime() + 10000;
        refresh();
    }

    private boolean allowed() {
        return Prefs.is(service, ON, true) && !service.busy()
                && service.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_PORTRAIT
                && service.getSystemService(PowerManager.class).isInteractive()
                && !service.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private void update() {
        if (!allowed()) { remove(false); return; }
        read();
        if (kind == NONE) { remove(true); return; }
        if (window == null) show();
        else window.bind();
    }

    private void read() {
        findPlayer();
        long end = MusicListener.countdown();
        if (end == 0) end = Prefs.of(service).getLong(TIMER_END, 0);
        long now = System.currentTimeMillis();
        timerEnd = end > now ? end : 0;
        // Lagu yang dijeda tetap tampil selagi kapsulnya sedang dibuka.
        boolean music = player != null && (playing || expanded && kind == MUSIC);
        int next = timerEnd > 0 ? TIMER : music ? MUSIC
                : SystemClock.elapsedRealtime() < chargeUntil ? CHARGE : NONE;
        if (next == CHARGE) {
            Intent battery = service.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) next = NONE;
            else level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100
                    / Math.max(1, battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        }
        kind = next;
    }

    private void findPlayer() {
        MediaController found = null;
        playing = false;
        try {
            for (MediaController c : service.getSystemService(MediaSessionManager.class)
                    .getActiveSessions(new ComponentName(service, MusicListener.class))) {
                PlaybackState s = c.getPlaybackState();
                boolean on = s != null && s.getState() == PlaybackState.STATE_PLAYING;
                if (found == null || on) found = c;
                if (on) { playing = true; break; }
            }
        } catch (SecurityException ignored) {
            // Izin akses notifikasi belum diberikan.
        }
        player = found;
        cover = null;
        title = artist = "";
        MediaMetadata m = found == null ? null : found.getMetadata();
        if (m == null) return;
        CharSequence name = m.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence by = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        title = name == null ? "" : name.toString();
        artist = by == null ? "" : by.toString();
        cover = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (cover == null) cover = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (cover == null) cover = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
    }

    private void show() {
        expanded = false;
        Window made = new Window();
        WindowManager.LayoutParams lp = service.params(-2, -2,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = made.dp(Prefs.of(service).getInt(TOP, 2));
        made.bind();
        try { wm.addView(made, lp); }
        catch (RuntimeException e) { return; }
        window = made;
        android.util.Log.i("ControlCenter", "kapsul tampil: " + kind);
        View content = made.getChildAt(0);
        content.setScaleX(0.25f);
        content.setScaleY(0.6f);
        content.setAlpha(0);
        content.animate().scaleX(1).scaleY(1).alpha(1).setDuration(460)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();
    }

    private void remove(boolean animated) {
        Window old = window;
        if (old == null) return;
        window = null;
        expanded = false;
        handler.removeCallbacks(autoCollapse);
        Runnable gone = () -> { if (old.isAttachedToWindow()) wm.removeView(old); };
        View content = old.getChildAt(0);
        if (!animated || content == null) { gone.run(); return; }
        content.animate().scaleX(0.2f).scaleY(0.5f).alpha(0).setDuration(240)
                .setInterpolator(new AccelerateInterpolator()).start();
        handler.postDelayed(gone, 260);
    }

    private void expand(boolean open) {
        handler.removeCallbacks(autoCollapse);
        if (window == null || expanded == open) return;
        expanded = open;
        if (open) handler.postDelayed(autoCollapse, 7000);
        else read();
        if (kind == NONE) { remove(true); return; }
        window.swap();
    }

    private static String clock(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    /** Jendela kapsul; isinya kapsul kecil atau kartu yang sudah membesar. */
    private class Window extends FrameLayout {
        private String built = "";
        private TextView time;
        private ImageView toggle;

        Window() {
            super(service);
            Glass.load(service);
        }

        void bind() {
            String now = expanded + "|" + kind + "|" + title + "|" + artist + "|"
                    + (cover != null) + "|" + (kind == CHARGE ? level : 0);
            if (!now.equals(built)) {
                built = now;
                removeAllViews();
                time = null;
                toggle = null;
                View content = expanded ? card() : new Capsule();
                addView(content, expanded
                        ? new LayoutParams(Math.min(getResources().getDisplayMetrics()
                                .widthPixels - dp(20), dp(380)), -2)
                        : new LayoutParams(dp(112), dp(30)));
            }
            if (time != null) time.setText(clock(timerEnd - System.currentTimeMillis()));
            if (toggle != null) {
                toggle.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            }
            if (getChildCount() > 0) getChildAt(0).invalidate();
        }

        /** Ganti ukuran dengan gerakan membesar atau mengecil. */
        void swap() {
            built = "";
            bind();
            View content = getChildAt(0);
            content.setAlpha(0.2f);
            content.setScaleX(expanded ? 0.35f : 1.5f);
            content.setScaleY(expanded ? 0.3f : 1.4f);
            content.setPivotY(0);
            content.post(() -> content.setPivotX(content.getWidth() / 2f));
            content.animate().alpha(1).scaleX(1).scaleY(1).setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.1f)).start();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE && expanded) expand(false);
            return super.onTouchEvent(e);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            // Selama disentuh jangan mengecil sendiri.
            if (expanded && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                handler.removeCallbacks(autoCollapse);
                handler.postDelayed(autoCollapse, 7000);
            }
            return super.dispatchTouchEvent(e);
        }

        private View card() {
            LinearLayout card = new LinearLayout(service);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(18), dp(16), dp(18), dp(14));
            card.setBackground(new Glass(service, 34, BLACK));
            card.setOnClickListener(v -> expand(false));
            if (kind == MUSIC) music(card);
            else if (kind == TIMER) timer(card);
            else charge(card);
            return card;
        }

        private void music(LinearLayout card) {
            LinearLayout head = row();
            ImageView art = new ImageView(service);
            if (cover != null) {
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageBitmap(cover);
            } else {
                art.setPadding(dp(14), dp(14), dp(14), dp(14));
                art.setBackgroundColor(0x33FFFFFF);
                art.setImageResource(R.drawable.ic_music);
            }
            art.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
                }
            });
            art.setClipToOutline(true);
            art.setContentDescription("Buka aplikasi musik");
            art.setOnClickListener(v -> open());
            head.addView(art, new LinearLayout.LayoutParams(dp(58), dp(58)));
            LinearLayout words = new LinearLayout(service);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(dp(14), 0, 0, 0);
            words.addView(line(title.isEmpty() ? "Sedang diputar" : title, 17, Color.WHITE, true));
            if (!artist.isEmpty()) words.addView(line(artist, 13, 0xB3FFFFFF, false));
            head.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(head);
            LinearLayout buttons = row();
            buttons.setPadding(0, dp(8), 0, 0);
            control(buttons, R.drawable.ic_previous, "Sebelumnya", 13, t -> t.skipToPrevious());
            toggle = control(buttons, R.drawable.ic_play, "Putar atau jeda", 9, t -> {
                if (playing) t.pause(); else t.play();
            });
            control(buttons, R.drawable.ic_next, "Berikutnya", 13, t -> t.skipToNext());
            card.addView(buttons);
        }

        private ImageView control(LinearLayout row, int icon, String label, int inset,
                java.util.function.Consumer<MediaController.TransportControls> action) {
            ImageView b = new ImageView(service);
            b.setImageResource(icon);
            b.setContentDescription(label);
            b.setPadding(dp(inset), dp(inset), dp(inset), dp(inset));
            b.setOnClickListener(v -> {
                if (player == null) return;
                Haptic.click(service);
                action.accept(player.getTransportControls());
                postDelayed(Island.this::refresh, 250);
            });
            Motion.press(b);
            row.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
            return b;
        }

        private void open() {
            if (player == null || player.getSessionActivity() == null) return;
            try { player.getSessionActivity().send(); }
            catch (Exception ignored) { }
            expand(false);
        }

        private void timer(LinearLayout card) {
            LinearLayout head = row();
            head.addView(icon(R.drawable.ic_hourglass, AMBER), new LinearLayout.LayoutParams(
                    dp(40), dp(40)));
            TextView name = line("Hitung mundur", 15, 0xB3FFFFFF, false);
            name.setPadding(dp(12), 0, 0, 0);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            time = line("", 36, AMBER, false);
            head.addView(time);
            card.addView(head);
            LinearLayout buttons = row();
            buttons.setPadding(0, dp(12), 0, 0);
            pill(buttons, "Buka jam", () -> {
                try {
                    service.startActivity(new Intent(AlarmClock.ACTION_SHOW_TIMERS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) { }
                expand(false);
            });
            pill(buttons, "Sembunyikan", () -> {
                // Hanya menyembunyikan kapsul; pengatur waktunya ada di aplikasi Jam.
                Prefs.of(service).edit().remove(TIMER_END).apply();
                MusicListener.hideCountdown();
                expanded = false;
                refresh();
            });
            card.addView(buttons);
        }

        private void charge(LinearLayout card) {
            LinearLayout head = row();
            head.addView(icon(R.drawable.ic_bolt, GREEN), new LinearLayout.LayoutParams(
                    dp(40), dp(40)));
            TextView name = line("Mengisi daya", 16, Color.WHITE, true);
            name.setPadding(dp(12), 0, 0, 0);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(line(level + "%", 30, GREEN, false));
            card.addView(head);
        }

        private void pill(LinearLayout row, String label, Runnable click) {
            TextView t = line(label, 14, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(new Glass(service, 22, 0x1FFFFFFF));
            t.setOnClickListener(v -> click.run());
            Motion.press(t);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            p.setMargins(dp(4), 0, dp(4), 0);
            row.addView(t, p);
        }

        private ImageView icon(int res, int color) {
            ImageView v = new ImageView(service);
            v.setImageResource(res);
            v.setColorFilter(color);
            v.setPadding(dp(7), dp(7), dp(7), dp(7));
            return v;
        }

        private LinearLayout row() {
            LinearLayout r = new LinearLayout(service);
            r.setGravity(Gravity.CENTER_VERTICAL);
            return r;
        }

        private TextView line(String value, int size, int color, boolean bold) {
            TextView t = new TextView(service);
            t.setText(value);
            t.setTextSize(size);
            t.setTextColor(color);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
            return t;
        }

        int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    }

    /** Bentuk kecil: gambar di kiri, keterangan hidup di kanan. */
    private class Capsule extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Capsule() {
            super(service);
            setContentDescription("Kapsul melayang");
            setOnClickListener(v -> { Haptic.click(service); expand(true); });
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float d = getResources().getDisplayMetrics().density;
            paint.setShader(null);
            Glass.draw(c, paint, new RectF(0, 0, w, h), h / 2, d, BLACK);
            float cy = h / 2, r = h / 2 - 5 * d, left = h / 2, right = w - 10 * d;
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(13 * d);
            paint.setTextAlign(Paint.Align.RIGHT);
            if (kind == MUSIC) {
                if (cover != null) {
                    BitmapShader shader = new BitmapShader(cover, Shader.TileMode.CLAMP,
                            Shader.TileMode.CLAMP);
                    float scale = 2 * r / Math.min(cover.getWidth(), cover.getHeight());
                    Matrix m = new Matrix();
                    m.setScale(scale, scale);
                    m.postTranslate(left - cover.getWidth() * scale / 2,
                            cy - cover.getHeight() * scale / 2);
                    shader.setLocalMatrix(m);
                    paint.setColor(0xFFFFFFFF);
                    paint.setShader(shader);
                    c.drawCircle(left, cy, r, paint);
                    paint.setShader(null);
                } else {
                    symbol(c, R.drawable.ic_music, left, cy, r, Color.WHITE);
                }
                // Batang suara yang naik turun.
                paint.setColor(GREEN);
                double t = SystemClock.uptimeMillis() / 130.0;
                for (int i = 0; i < 4; i++) {
                    float tall = playing
                            ? (float) (0.35 + 0.65 * Math.abs(Math.sin(t * (0.5 + i * 0.17) + i)))
                            : 0.25f;
                    float x = right - (3 - i) * 5 * d - 3 * d, half = tall * r;
                    c.drawRoundRect(x, cy - half, x + 3 * d, cy + half, 2 * d, 2 * d, paint);
                }
                if (playing) postInvalidateDelayed(90);
            } else {
                boolean timer = kind == TIMER;
                int color = timer ? AMBER : GREEN;
                symbol(c, timer ? R.drawable.ic_hourglass : R.drawable.ic_bolt, left, cy, r, color);
                paint.setColor(color);
                String value = timer ? clock(timerEnd - System.currentTimeMillis()) : level + "%";
                c.drawText(value, right, cy - (paint.ascent() + paint.descent()) / 2, paint);
            }
        }

        private void symbol(Canvas c, int res, float x, float y, float r, int color) {
            Drawable icon = getContext().getDrawable(res).mutate();
            int half = Math.round(r * 0.8f);
            icon.setBounds(Math.round(x) - half, Math.round(y) - half,
                    Math.round(x) + half, Math.round(y) + half);
            icon.setTint(color);
            icon.draw(c);
        }
    }
}
