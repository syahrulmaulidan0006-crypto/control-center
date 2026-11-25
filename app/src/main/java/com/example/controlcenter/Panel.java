package com.example.controlcenter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Isi Control Center: kotak koneksi, musik, slider, tombol, dan lapisan mengambang. */
class Panel extends FrameLayout {
    private final OverlayService service;
    private final Controls controls;
    private final ScrollView scroll;
    private final LinearLayout box;
    private final List<TileView> tiles = new ArrayList<>();
    private final List<TextView> states = new ArrayList<>();
    private final List<Item> stateItems = new ArrayList<>();
    private final int width, unit, gap;
    private View connections;
    private FrameLayout expanded, popup;
    private float downX, downY, progress;
    private boolean dragging, collapsing;
    private VelocityTracker velocity;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            refresh(true);
            postDelayed(this, 600);
        }
    };

    Panel(OverlayService service, Controls controls) {
        super(service);
        this.service = service;
        this.controls = controls;
        DisplayMetrics screen = getResources().getDisplayMetrics();
        gap = dp(14);
        int max = Math.min(Math.min(screen.widthPixels - dp(44), dp(400)),
                Math.max(dp(280), screen.heightPixels));
        unit = (max - 3 * gap) / 4;
        width = unit * 4 + gap * 3;

        box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        buildConnections();
        buildMusic();
        buildMiddle();
        buildShortcuts();

        FrameLayout wrap = new FrameLayout(service);
        wrap.setPadding(0, dp(44), 0, dp(40));
        wrap.setClipChildren(false);
        wrap.addView(box, new LayoutParams(width, -2, Gravity.CENTER));
        wrap.setOnClickListener(v -> service.closePanel());
        scroll = new ScrollView(service);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(wrap);
        addView(scroll, new LayoutParams(-1, -1));
        refresh(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(poll);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(poll);
        super.onDetachedFromWindow();
    }

    private void refresh(boolean animate) {
        for (TileView t : tiles) t.setOn(controls.isOn(t.item), animate);
        for (int i = 0; i < states.size(); i++) {
            Item item = stateItems.get(i);
            states.get(i).setText(item == Item.HOTSPOT ? "Pengaturan"
                    : controls.isOn(item) ? "Aktif" : "Mati");
        }
    }

    // ---------------------------------------------------------------- Bagian panel

    private TileView tile(Item item) {
        TileView t = new TileView(getContext(), item);
        t.setOnClickListener(v -> {
            controls.click(item);
            postDelayed(() -> refresh(true), 150);
        });
        if (item == Item.WIFI || item == Item.DATA || item == Item.BLUETOOTH) {
            t.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                showPicker(item);
                return true;
            });
        }
        tiles.add(t);
        return t;
    }

    private GradientDrawable glass(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), 0x40FFFFFF);
        return d;
    }

    private void addSection(View v, int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.bottomMargin = gap;
        box.addView(v, p);
    }

    private void buildConnections() {
        GridLayout card = new GridLayout(getContext());
        card.setColumnCount(3);
        card.setBackground(glass(0x30FFFFFF, 34));
        card.setPadding(0, dp(8), 0, dp(8));
        card.setClipChildren(false);
        int size = Math.min(unit, dp(64));
        Item[] list = {Item.AIRPLANE, Item.WIFI, Item.DATA,
                Item.BLUETOOTH, Item.HOTSPOT, Item.LOCATION};
        for (Item item : list) {
            FrameLayout cell = new FrameLayout(getContext());
            cell.setClipChildren(false);
            cell.addView(tile(item), new LayoutParams(size, size, Gravity.CENTER));
            // Area kosong di sekitar tombol membesarkan kotak.
            cell.setOnClickListener(v -> expand());
            cell.setOnLongClickListener(v -> { expand(); return true; });
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = width / 3;
            p.height = size + dp(18);
            card.addView(cell, p);
        }
        card.setOnClickListener(v -> expand());
        connections = card;
        addSection(card, width, -2);
    }

    private void buildMusic() {
        addSection(new MusicCard(getContext(), () -> {
            controls.toast("Buka aplikasi dan berikan izin akses notifikasi");
            controls.launch(new android.content.Intent(getContext(), MainActivity.class));
        }, service::closePanel), width, -2);
    }

    private void buildMiddle() {
        LinearLayout row = new LinearLayout(getContext());
        row.setClipChildren(false);
        GridLayout grid = grid(2);
        Item[] list = {Item.ROTATION, Item.DND, Item.TORCH, Item.VIBRATE};
        for (int i = 0; i < list.length; i++) cell(grid, tile(list[i]), i < 2 ? gap : 0);
        row.addView(grid, new LinearLayout.LayoutParams(unit * 2 + gap * 2, -1));

        int bright = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128);
        VerticalSlider light = new VerticalSlider(getContext(), R.drawable.ic_sun,
                Math.min(1f, bright / 255f), value -> {
            if (!Settings.System.canWrite(getContext())) return;
            controls.put(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            controls.put(Settings.System.SCREEN_BRIGHTNESS,
                    Math.max(1, Math.round(value * 255)));
        }, controls::canWrite);
        light.setContentDescription("Kecerahan");
        row.addView(light, new LinearLayout.LayoutParams(unit, -1));

        AudioManager audio = controls.audio;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        VerticalSlider sound = new VerticalSlider(getContext(), R.drawable.ic_volume,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max, value -> {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
            } catch (SecurityException e) { controls.toast("Izin Jangan Ganggu diperlukan"); }
        }, null);
        sound.setContentDescription("Volume");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(unit, -1);
        p.leftMargin = gap;
        row.addView(sound, p);
        addSection(row, width, unit * 2 + gap);
    }

    private void buildShortcuts() {
        GridLayout grid = grid(4);
        Item[] all = Item.values();
        for (int i = Item.CALCULATOR.ordinal(); i < all.length; i++) {
            cell(grid, tile(all[i]), gap);
        }
        addSection(grid, width + gap, -2);
    }

    private GridLayout grid(int columns) {
        GridLayout g = new GridLayout(getContext());
        g.setColumnCount(columns);
        g.setClipChildren(false);
        return g;
    }

    private void cell(GridLayout grid, View v, int bottom) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = unit;
        p.height = unit;
        p.setMargins(0, 0, gap, bottom);
        grid.addView(v, p);
    }

    // ---------------------------------------------------------------- Kotak membesar

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private void expand() {
        if (expanded != null) return;
        FrameLayout layer = new FrameLayout(getContext());
        layer.setOnClickListener(v -> collapse());
        GridLayout card = new GridLayout(getContext());
        card.setColumnCount(2);
        card.setBackground(glass(0x55FFFFFF, 40));
        card.setPadding(dp(8), dp(16), dp(8), dp(16));
        card.setClickable(true);
        card.setClipChildren(false);
        int first = tiles.size();
        Item[] list = {Item.AIRPLANE, Item.WIFI, Item.DATA,
                Item.BLUETOOTH, Item.HOTSPOT, Item.LOCATION};
        int rowHeight = Math.min(dp(132),
                (getHeight() - dp(80)) / 3);
        for (Item item : list) {
            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setClipChildren(false);
            int size = Math.min(dp(70), rowHeight - dp(44));
            cell.addView(tile(item), new LinearLayout.LayoutParams(size, size));
            TextView name = text(item.label, 13, Color.WHITE);
            name.setPadding(0, dp(6), 0, 0);
            cell.addView(name);
            TextView state = text("", 11, 0xB3FFFFFF);
            cell.addView(state);
            states.add(state);
            stateItems.add(item);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = (width - dp(16)) / 2;
            p.height = rowHeight;
            card.addView(cell, p);
        }
        layer.setTag(first);
        layer.addView(card, new LayoutParams(width, -2, Gravity.CENTER));
        expanded = layer;
        addView(layer, new LayoutParams(-1, -1));
        refresh(false);

        // Mulai dari posisi dan ukuran kotak kecil, lalu membesar.
        card.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                card.getViewTreeObserver().removeOnPreDrawListener(this);
                morph(card, true, null);
                return true;
            }
        });
        scroll.animate().alpha(0.08f).setDuration(260).start();
    }

    private void morph(View card, boolean open, Runnable end) {
        int[] from = new int[2], to = new int[2];
        connections.getLocationInWindow(from);
        ((View) card.getParent()).getLocationInWindow(to);
        float sx = connections.getWidth() / (float) card.getWidth();
        float sy = connections.getHeight() / (float) card.getHeight();
        float tx = from[0] + connections.getWidth() / 2f
                - (to[0] + card.getLeft() + card.getWidth() / 2f);
        float ty = from[1] + connections.getHeight() / 2f
                - (to[1] + card.getTop() + card.getHeight() / 2f);
        if (open) {
            card.setScaleX(sx);
            card.setScaleY(sy);
            card.setTranslationX(tx);
            card.setTranslationY(ty);
            card.setAlpha(0.3f);
        }
        card.animate().scaleX(open ? 1 : sx).scaleY(open ? 1 : sy)
                .translationX(open ? 0 : tx).translationY(open ? 0 : ty)
                .alpha(open ? 1 : 0).setDuration(open ? 360 : 240)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .withEndAction(end).start();
    }

    private void collapse() {
        FrameLayout layer = expanded;
        if (layer == null || collapsing) return;
        collapsing = true;
        scroll.animate().alpha(1).setDuration(240).start();
        morph(layer.getChildAt(0), false, () -> {
            removeView(layer);
            expanded = null;
            collapsing = false;
            int first = (Integer) layer.getTag();
            tiles.subList(first, tiles.size()).clear();
            states.clear();
            stateItems.clear();
        });
    }

    // ---------------------------------------------------------------- Jendela mengambang

    private void showPicker(Item item) {
        if (popup != null) return;
        popup = new Picker(getContext(), controls, item, width, this::closePicker);
        addView(popup, new LayoutParams(-1, -1));
        popup.setAlpha(0);
        popup.animate().alpha(1).setDuration(180).start();
        View card = popup.getChildAt(0);
        card.setScaleX(0.8f);
        card.setScaleY(0.8f);
        card.animate().scaleX(1).scaleY(1).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
    }

    private void closePicker() {
        FrameLayout old = popup;
        if (old == null) return;
        popup = null;
        service.setKeyboard(false);
        old.animate().alpha(0).setDuration(160).withEndAction(() -> removeView(old)).start();
    }

    // ---------------------------------------------------------------- Gerakan

    /** 0 = tertutup, 1 = terbuka penuh. */
    void setProgress(float value) {
        progress = value;
        float shown = Math.max(0, Math.min(1, value));
        setAlpha(Math.min(1, shown * 1.4f));
        float away = 1 - Math.min(1.06f, value);
        for (int i = 0; i < box.getChildCount(); i++) {
            // Tiap bagian bergerak sedikit berbeda sehingga terasa berurutan.
            box.getChildAt(i).setTranslationY(-away * dp(70 + i * 34));
        }
        float scale = 0.92f + 0.08f * shown;
        box.setPivotX(width / 2f);
        box.setPivotY(0);
        box.setScaleX(scale);
        box.setScaleY(scale);
    }

    float progress() { return progress; }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (popup != null) closePicker();
                else if (expanded != null) collapse();
                else service.closePanel();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Geser ke atas saat sudah di bagian bawah: panel mengikuti jari. */
    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (popup != null || expanded != null) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getRawX();
            downY = e.getRawY();
            dragging = false;
        } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dy = e.getRawY() - downY;
            if (-dy > ViewConfiguration.get(getContext()).getScaledTouchSlop()
                    && -dy > Math.abs(e.getRawX() - downX) * 1.5f
                    && !scroll.canScrollVertically(1)) {
                dragging = true;
                downY = e.getRawY();
                service.stopAnimation();
                velocity = VelocityTracker.obtain();
                return true;
            }
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return super.onTouchEvent(e);
        MotionEvent raw = MotionEvent.obtain(e);
        raw.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(raw);
        raw.recycle();
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_MOVE) {
            float p = 1 + Math.min(0, e.getRawY() - downY) / dp(360);
            service.drag(Math.max(0, p));
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            velocity.computeCurrentVelocity(1000);
            boolean close = velocity.getYVelocity() < -dp(500) || progress < 0.6f;
            velocity.recycle();
            dragging = false;
            if (close) service.closePanel(); else service.openFully();
        }
        return true;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
