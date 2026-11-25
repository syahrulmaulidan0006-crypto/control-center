package com.example.controlcenter;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.List;

/** Kartu musik yang tersinkron dengan sesi media aktif. */
class MusicCard extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final MediaSessionManager sessions;
    private final ImageView art, play;
    private final TextView title, artist;
    private final SeekBar progress;
    private MediaController controller;
    private boolean listening, seeking;
    private Runnable onOpen = () -> { };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onSessionDestroyed() { select(null); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::select;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    MusicCard(Context context, Runnable needPermission, Runnable closePanel) {
        super(context);
        audio = context.getSystemService(AudioManager.class);
        sessions = context.getSystemService(MediaSessionManager.class);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(16), dp(16), dp(8));

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(context);
        art.setContentDescription("Sampul album");
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
            }
        });
        art.setClipToOutline(true);
        art.setOnClickListener(v -> {
            if (!listening) { needPermission.run(); return; }
            if (controller != null && controller.getSessionActivity() != null) {
                onOpen = closePanel;
                try { controller.getSessionActivity().send(); onOpen.run(); }
                catch (Exception ignored) { }
            }
        });
        top.addView(art, new LayoutParams(dp(64), dp(64)));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        title = line(17, Color.WHITE);
        artist = line(14, 0xB3FFFFFF);
        info.addView(title);
        info.addView(artist);
        top.addView(info, new LayoutParams(0, -2, 1));
        addView(top, new LayoutParams(-1, -2));

        progress = new SeekBar(context);
        progress.setMax(1000);
        progress.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        progress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0x80FFFFFF));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean user) { }
            @Override public void onStartTrackingTouch(SeekBar b) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar b) {
                seeking = false;
                long length = duration();
                if (controller != null && length > 0) {
                    controller.getTransportControls().seekTo(length * b.getProgress() / 1000);
                }
            }
        });
        addView(progress, new LayoutParams(-1, dp(30)));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.CENTER);
        button(buttons, R.drawable.ic_previous, "Sebelumnya", 48,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        play = button(buttons, R.drawable.ic_play, "Putar atau jeda", 56,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        button(buttons, R.drawable.ic_next, "Berikutnya", 48, KeyEvent.KEYCODE_MEDIA_NEXT);
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

    private long duration() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        return m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
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
        title.setText(name);
        artist.setText(by);
        title.setVisibility(name == null ? GONE : VISIBLE);
        artist.setVisibility(by == null ? GONE : VISIBLE);

        // Warna kartu mengikuti warna rata-rata sampul album.
        int tint = 0x38FFFFFF;
        if (cover != null) {
            art.setPadding(0, 0, 0, 0);
            art.setBackground(null);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageBitmap(cover);
            try {
                Bitmap one = Bitmap.createScaledBitmap(cover, 1, 1, true);
                tint = (one.getPixel(0, 0) & 0x00FFFFFF) | 0x80000000;
            } catch (Exception ignored) { }
        } else {
            art.setPadding(dp(16), dp(16), dp(16), dp(16));
            art.setBackgroundColor(0x38FFFFFF);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(R.drawable.ic_music);
        }
        GradientDrawable back = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{tint, 0x30FFFFFF});
        back.setCornerRadius(dp(32));
        back.setStroke(dp(1), 0x40FFFFFF);
        setBackground(back);
        play.setImageResource(playing() ? R.drawable.ic_pause : R.drawable.ic_play);
        updateProgress();
    }

    private void updateProgress() {
        if (controller == null) play.setImageResource(
                audio.isMusicActive() ? R.drawable.ic_pause : R.drawable.ic_play);
        long length = duration();
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        progress.setEnabled(length > 0);
        if (seeking) return;
        if (s == null || length <= 0) { progress.setProgress(0); return; }
        long position = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            position += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime())
                    * s.getPlaybackSpeed());
        }
        progress.setProgress((int) Math.max(0, Math.min(1000, position * 1000 / length)));
    }

    private ImageView button(LinearLayout row, int res, String label, int size, int key) {
        ImageView b = new ImageView(getContext());
        b.setImageResource(res);
        b.setContentDescription(label);
        b.setPadding(dp(11), dp(11), dp(11), dp(11));
        b.setOnClickListener(v -> send(key));
        LayoutParams p = new LayoutParams(dp(size), dp(size));
        p.setMargins(dp(18), 0, dp(18), 0);
        row.addView(b, p);
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
