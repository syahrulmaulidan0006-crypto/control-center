package com.example.controlcenter;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.function.Consumer;

/** Kartu musik yang tersinkron dengan sesi media aktif. */
class MusicCard extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final MediaSessionManager sessions;
    private final ImageView art, play, previous, next;
    private final TextView title, artist;
    private MediaController controller;
    private boolean listening;
    private Boolean shownPlaying;
    private Runnable onOpen = () -> { };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onSessionDestroyed() { select(null); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::select;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updatePlay();
            handler.postDelayed(this, 500);
        }
    };

    MusicCard(Context context, int side, Consumer<String> output, Runnable needPermission,
            Runnable closePanel) {
        super(context);
        audio = context.getSystemService(AudioManager.class);
        sessions = context.getSystemService(MediaSessionManager.class);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(12), dp(12), dp(6));

        // Atas: sampul kecil di kiri, tombol keluaran suara di kanan.
        LinearLayout top = new LinearLayout(context);
        art = new ImageView(context);
        art.setContentDescription("Sampul album");
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(11));
            }
        });
        art.setClipToOutline(true);
        art.setOnClickListener(v -> {
            if (!listening) { needPermission.run(); return; }
            if (controller != null && controller.getSessionActivity() != null) {
                try { controller.getSessionActivity().send(); closePanel.run(); }
                catch (Exception ignored) { }
            }
        });
        // 66 dp, diperkecil bila kotaknya sempit atau huruf sistem diperbesar,
        // supaya judul, artis, dan tombol tetap muat.
        int words = Math.round(40 * getResources().getDisplayMetrics().scaledDensity);
        int artSize = Math.max(dp(36), Math.min(dp(66), side - dp(62) - words));
        top.addView(art, new LayoutParams(artSize, artSize));
        top.addView(new View(context), new LayoutParams(0, 1, 1));
        ImageView out = new ImageView(context);
        out.setImageResource(R.drawable.ic_output);
        out.setContentDescription("Keluaran suara");
        out.setPadding(dp(6), dp(6), dp(6), dp(6));
        out.setBackground(new Glass(context, 15, 0x26FFFFFF));
        out.setOnClickListener(v -> {
            pop(v);
            output.accept(controller == null ? null : controller.getPackageName());
        });
        top.addView(out, new LayoutParams(dp(30), dp(30)));
        addView(top, new LayoutParams(-1, -2));
        addView(new View(context), new LayoutParams(1, 0, 1));

        title = line(15, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        artist = line(12, 0xB3FFFFFF);
        title.setPadding(dp(3), 0, dp(3), 0);
        artist.setPadding(dp(3), 0, dp(3), 0);
        addView(title, new LayoutParams(-1, -2));
        addView(artist, new LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.CENTER);
        previous = button(buttons, R.drawable.ic_previous, "Sebelumnya",
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, 10);
        play = button(buttons, R.drawable.ic_play, "Putar atau jeda",
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 5);
        next = button(buttons, R.drawable.ic_next, "Berikutnya",
                KeyEvent.KEYCODE_MEDIA_NEXT, 10);
        addView(buttons, new LayoutParams(-1, -2));
        refresh();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ComponentName name = new ComponentName(getContext(), MusicListener.class);
        try {
            sessions.addOnActiveSessionsChangedListener(changed, name, handler);
            listening = true;
            select(sessions.getActiveSessions(name));
        } catch (SecurityException e) {
            // Izin akses notifikasi belum diberikan.
            listening = false;
            refresh();
        }
        handler.post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        if (listening) sessions.removeOnActiveSessionsChangedListener(changed);
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
        super.onDetachedFromWindow();
    }

    private void select(List<MediaController> list) {
        MediaController next = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { next = c; break; }
            }
            if (next == null && !list.isEmpty()) next = list.get(0);
        }
        if (controller != null) controller.unregisterCallback(callback);
        controller = next;
        if (controller != null) controller.registerCallback(callback, handler);
        refresh();
    }

    private boolean playing() {
        if (controller == null) return audio.isMusicActive();
        PlaybackState s = controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    private void refresh() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        Bitmap cover = null;
        CharSequence name = null, by = null;
        if (m != null) {
            name = m.getText(MediaMetadata.METADATA_KEY_TITLE);
            if (name == null) name = m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            by = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (by == null) by = m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            cover = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (cover == null) cover = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (cover == null) cover = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        CharSequence heading = name != null && name.length() > 0 ? name
                : audio.isMusicActive() ? "Sedang diputar" : "Tidak diputar";
        if (!TextUtils.equals(title.getText(), heading)) {
            // Judul baru memudar masuk.
            title.setText(heading);
            title.setAlpha(0);
            title.animate().alpha(1).setDuration(320).start();
            art.setAlpha(0);
            art.setScaleX(0.8f);
            art.setScaleY(0.8f);
            art.animate().alpha(1).scaleX(1).scaleY(1).setDuration(380).start();
        }
        artist.setText(by);
        artist.setVisibility(by == null || by.length() == 0 ? GONE : VISIBLE);

        // Warna kartu mengikuti warna rata-rata sampul album.
        int tint = 0x1FFFFFFF;
        if (cover != null) {
            art.setPadding(0, 0, 0, 0);
            art.setBackground(null);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageBitmap(cover);
            try {
                Bitmap one = Bitmap.createScaledBitmap(cover, 1, 1, true);
                tint = (one.getPixel(0, 0) & 0x00FFFFFF) | 0x66000000;
            } catch (Exception ignored) { }
        } else {
            art.setPadding(dp(10), dp(10), dp(10), dp(10));
            art.setBackgroundColor(0x38FFFFFF);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(R.drawable.ic_music);
        }
        setBackground(new Glass(getContext(), 32, tint));
        updatePlay();
    }

    private void updatePlay() {
        boolean now = playing();
        float side = now || controller != null ? 1 : 0.45f;
        previous.setAlpha(side);
        next.setAlpha(side);
        if (shownPlaying != null && shownPlaying == now) return;
        boolean first = shownPlaying == null;
        shownPlaying = now;
        play.setImageResource(now ? R.drawable.ic_pause : R.drawable.ic_play);
        if (!first) pop(play);
    }

    private void pop(View v) {
        v.setScaleX(0.55f);
        v.setScaleY(0.55f);
        v.animate().scaleX(1).scaleY(1).setDuration(320)
                .setInterpolator(new OvershootInterpolator(2.5f)).start();
    }

    private ImageView button(LinearLayout row, int res, String label, int key, int inset) {
        ImageView b = new ImageView(getContext());
        b.setImageResource(res);
        b.setContentDescription(label);
        b.setPadding(dp(inset), dp(inset), dp(inset), dp(inset));
        b.setOnClickListener(v -> { pop(v); send(key); });
        row.addView(b, new LayoutParams(0, dp(40), 1));
        return b;
    }

    private void send(int key) {
        if (controller == null) {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
            return;
        }
        MediaController.TransportControls t = controller.getTransportControls();
        if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) t.skipToPrevious();
        else if (key == KeyEvent.KEYCODE_MEDIA_NEXT) t.skipToNext();
        else if (playing()) t.pause();
        else t.play();
    }

    private TextView line(int size, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(size);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
