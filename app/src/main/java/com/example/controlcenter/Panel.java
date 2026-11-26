package com.example.controlcenter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
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
class Panel extends Sheet {
    private final Controls controls;
    private final LinearLayout box;
    private final List<TileView> tiles = new ArrayList<>();
    private final List<TextView> states = new ArrayList<>();
    private final List<Item> stateItems = new ArrayList<>();
    private final int width, unit, gap;
    private final boolean land;
    private View connections;
    private GridLayout middle, shortcuts;
    private final List<TileView> custom = new ArrayList<>();
    private Editor editor;
    private VerticalSlider light, sound;
    private FrameLayout expanded, popup;
    private boolean collapsing;
    private View source;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            refresh(true);
            postDelayed(this, 600);
        }
    };

    Panel(OverlayService service, Controls controls) {
        super(service);
        this.controls = controls;
        DisplayMetrics screen = getResources().getDisplayMetrics();
        // Layar mendatar (mode game): tiga kolom yang pas tanpa perlu digulir.
        land = screen.widthPixels > screen.heightPixels;
        gap = dp(land ? 10 : 14);
        if (land) {
            unit = Math.min((screen.heightPixels - dp(70) - 4 * gap) / 4,
                    (screen.widthPixels - dp(80) - 8 * gap) / 8);
            width = unit * 8 + gap * 8;
        } else {
            int max = Math.min(screen.widthPixels - dp(44), dp(400));
            int cell = (max - 3 * gap) / 4;
            unit = cell;
            width = cell * 4 + gap * 3;
        }

        box = new LinearLayout(service);
        box.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        box.setClipChildren(false);
        buildTop();
        buildMiddle();
        buildShortcuts();
        fill();
        box.setOnLongClickListener(v -> { edit(); return true; });

        FrameLayout wrap = new FrameLayout(service);
        wrap.setPadding(0, dp(land ? 14 : 40), 0, dp(land ? 8 : 40));
        wrap.setClipChildren(false);
        LinearLayout page = new LinearLayout(service);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        LinearLayout.LayoutParams statusSize = new LinearLayout.LayoutParams(-1, dp(24));
        statusSize.bottomMargin = dp(land ? 6 : 16);
        LinearLayout header = new LinearLayout(service);
        header.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView add = new android.widget.ImageView(service);
        add.setImageResource(R.drawable.ic_plus);
        add.setContentDescription("Atur tombol");
        add.setPadding(dp(6), dp(6), dp(6), dp(6));
        add.setBackground(new Glass(service, 16, 0x26FFFFFF));
        add.setOnClickListener(v -> edit());
        Motion.press(add);
        LinearLayout.LayoutParams addSize = new LinearLayout.LayoutParams(
                dp(land ? 24 : 30), dp(land ? 24 : 30));
        addSize.rightMargin = dp(10);
        header.addView(add, addSize);
        header.addView(new StatusRow(service), new LinearLayout.LayoutParams(0, dp(24), 1));
        statusSize.height = dp(land ? 24 : 30);
        page.addView(header, statusSize);
        page.addView(box, new LinearLayout.LayoutParams(-1, -2));
        wrap.addView(page, new LayoutParams(width, -2, Gravity.CENTER));
        wrap.setOnClickListener(v -> service.closePanel());
        wrap.setOnLongClickListener(v -> { edit(); return true; });
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
        post(levels);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(poll);
        removeCallbacks(levels);
        super.onDetachedFromWindow();
    }

    private final Runnable levels = new Runnable() {
        @Override public void run() {
            syncLevels();
            postDelayed(this, 150);
        }
    };

    private void syncLevels() {
        if (sound == null || light == null) return;
        AudioManager audio = controls.audio;
        sound.sync(audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                / (float) audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        light.sync(Math.min(1f, Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128) / 255f));
    }

    /** Tombol volume fisik langsung menggerakkan slider. */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        int key = e.getKeyCode();
        if (key == android.view.KeyEvent.KEYCODE_VOLUME_UP
                || key == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                try {
                    controls.audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            key == android.view.KeyEvent.KEYCODE_VOLUME_UP
                                    ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                } catch (SecurityException ignored) { }
                syncLevels();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void refresh(boolean animate) {
        for (TileView t : tiles) {
            t.setOn(controls.isOn(t.item), animate);
            if (t.item == Item.DND) t.face(Focus.icon(getContext()));
        }
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
            // Beri waktu agar gerakan ikon terlihat sebelum panel ditutup.
            if (item.leaves()) postDelayed(() -> controls.click(item), 240);
            else controls.click(item);
            postDelayed(() -> refresh(true), 150);
        });
        // Tekan lama membuka jendela mengambang milik tombol itu.
        t.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showPicker(item, v);
            return true;
        });
        tiles.add(t);
        return t;
    }

    private void addSection(View v, int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        if (land) p.rightMargin = box.getChildCount() < 2 ? gap : 0;
        else p.bottomMargin = gap;
        box.addView(v, p);
    }

    /** Baris atas: kotak koneksi dan kotak musik, dua persegi bersebelahan. */
    private void buildTop() {
        int side = unit * 2 + gap;
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(land ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setClipChildren(false);

        GridLayout card = grid(2);
        card.setBackground(new Glass(getContext(), 32, 0x1FFFFFFF));
        int pad = dp(6);
        card.setPadding(pad, pad, pad, pad);
        int cell = (side - pad * 2) / 2;
        int size = Math.round(cell * 0.8f);
        Item[] list = {Item.AIRPLANE, Item.WIFI, Item.DATA, Item.BLUETOOTH};
        for (Item item : list) {
            FrameLayout holder = new FrameLayout(getContext());
            holder.setClipChildren(false);
            holder.addView(tile(item), new LayoutParams(size, size, Gravity.CENTER));
            // Area kosong di sekitar tombol membesarkan kotak.
            holder.setOnClickListener(v -> expand());
            holder.setOnLongClickListener(v -> { expand(); return true; });
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = cell;
            p.height = cell;
            card.addView(holder, p);
        }
        card.setOnClickListener(v -> expand());
        connections = card;
        row.addView(card, new LinearLayout.LayoutParams(side, side));

        MusicCard music = new MusicCard(getContext(), side, this::output, () -> {
            controls.toast("Buka aplikasi dan berikan izin akses notifikasi");
            controls.launch(new android.content.Intent(getContext(), MainActivity.class));
        }, service::closePanel);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(side, side);
        if (land) m.topMargin = gap; else m.leftMargin = gap;
        row.addView(music, m);
        if (land) addSection(row, side, side * 2 + gap);
        else addSection(row, width, side);
    }

    /** Memilih keluaran suara (speaker, headset, Bluetooth). */
    private void output(String player) {
        android.content.Intent volume =
                new android.content.Intent(Settings.Panel.ACTION_VOLUME);
        if (player == null) { controls.launch(volume); return; }
        controls.launch(new android.content.Intent(
                "com.android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", player), volume);
    }

    private void buildMiddle() {
        LinearLayout row = new LinearLayout(getContext());
        row.setClipChildren(false);
        LinearLayout sliders = row;
        middle = grid(2);
        GridLayout grid = middle;
        if (land) {
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(grid, new LinearLayout.LayoutParams(-2, unit * 2 + gap));
            sliders = new LinearLayout(getContext());
            sliders.setClipChildren(false);
            LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, unit * 2 + gap);
            under.topMargin = gap;
            row.addView(sliders, under);
        } else {
            row.addView(grid, new LinearLayout.LayoutParams(unit * 2 + gap * 2, -1));
        }

        int bright = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128);
        light = new VerticalSlider(getContext(), R.drawable.ic_sun,
                Math.min(1f, bright / 255f), value -> {
            if (!Settings.System.canWrite(getContext())) return;
            controls.put(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            controls.put(Settings.System.SCREEN_BRIGHTNESS,
                    Math.max(1, Math.round(value * 255)));
        }, controls::canWrite);
        light.setContentDescription("Kecerahan");
        light.onHold = () -> showPicker(Item.AUTO_LIGHT, light);
        sliders.addView(light, new LinearLayout.LayoutParams(unit, -1));

        AudioManager audio = controls.audio;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        sound = new VerticalSlider(getContext(), R.drawable.ic_volume,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max, value -> {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
            } catch (SecurityException e) { controls.toast("Izin Jangan Ganggu diperlukan"); }
        }, null);
        sound.setContentDescription("Volume");
        sound.onHold = () -> showPicker(Item.VOLUME, sound);
        sound.levelIcons(R.drawable.ic_volume0, R.drawable.ic_volume1);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(unit, -1);
        p.leftMargin = gap;
        sliders.addView(sound, p);
        if (land) addSection(row, unit * 2 + gap, unit * 4 + gap * 3);
        else addSection(row, width, unit * 2 + gap);
    }

    private void buildShortcuts() {
        shortcuts = grid(4);
        addSection(shortcuts, land ? unit * 4 + gap * 4 : width + gap, -2);
    }

    /** Empat tombol pertama berada di samping slider, sisanya di kisi bawah. */
    private void fill() {
        tiles.removeAll(custom);
        custom.clear();
        middle.removeAllViews();
        shortcuts.removeAllViews();
        List<Item> order = Item.load(getContext());
        for (int i = 0; i < Math.max(4, order.size()); i++) {
            View v;
            if (i < order.size()) {
                TileView t = tile(order.get(i));
                custom.add(t);
                v = t;
            } else {
                v = new View(getContext());
            }
            if (i < 4) {
                cell(middle, v, i < 2 ? gap : 0);
                // Dalam mode mendatar kolom kanan tidak memakai jarak tambahan.
                if (land && i % 2 == 1) {
                    ((GridLayout.LayoutParams) v.getLayoutParams()).rightMargin = 0;
                }
            } else {
                cell(shortcuts, v, gap);
            }
        }
        refresh(false);
    }

    // ---------------------------------------------------------------- Atur tombol

    private void edit() {
        if (editor != null || popup != null || expanded != null) return;
        editor = new Editor(getContext(), width, land ? 8 : 4, this::closeEditor);
        addView(editor, new LayoutParams(-1, -1));
        service.dots(false);
        blur(scroll, true);
        scroll.animate().alpha(0).setDuration(220).start();
        editor.setAlpha(0);
        editor.setScaleX(1.08f);
        editor.setScaleY(1.08f);
        editor.animate().alpha(1).scaleX(1).scaleY(1).setDuration(280)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void closeEditor() {
        Editor old = editor;
        if (old == null) return;
        editor = null;
        service.dots(true);
        fill();
        blur(scroll, false);
        scroll.animate().alpha(1).setDuration(220).start();
        for (int i = 0; i < custom.size(); i++) {
            Motion.enter(custom.get(i), i * 18L, dp(18));
        }
        old.animate().alpha(0).setDuration(180).withEndAction(() -> removeView(old)).start();
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
        int columns = land ? 3 : 2;
        int wide = Math.min(width, dp(land ? 480 : 400));
        card.setColumnCount(columns);
        card.setBackground(new Glass(getContext(), 40, 0x33FFFFFF));
        card.setPadding(dp(8), dp(16), dp(8), dp(16));
        card.setClickable(true);
        card.setClipChildren(false);
        int first = tiles.size();
        Item[] list = {Item.AIRPLANE, Item.WIFI, Item.DATA,
                Item.BLUETOOTH, Item.HOTSPOT, Item.LOCATION};
        int rowHeight = Math.min(dp(132),
                (getHeight() - dp(80)) / (6 / columns));
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
            p.width = (wide - dp(16)) / columns;
            p.height = rowHeight;
            card.addView(cell, p);
        }
        layer.setTag(first);
        layer.addView(card, new LayoutParams(wide, -2, Gravity.CENTER));
        expanded = layer;
        addView(layer, new LayoutParams(-1, -1));
        refresh(false);

        // Mulai dari posisi dan ukuran kotak kecil, lalu membesar.
        card.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                card.getViewTreeObserver().removeOnPreDrawListener(this);
                morph(card, connections, true, null);
                return true;
            }
        });
        blur(scroll, true);
        scroll.animate().alpha(0.25f).setDuration(260).start();
    }

    /** Membesar dari tombol atau kotak asalnya, dan kembali ke sana saat ditutup. */
    private void morph(View card, View origin, boolean open, Runnable end) {
        int[] from = new int[2], to = new int[2];
        origin.getLocationInWindow(from);
        ((View) card.getParent()).getLocationInWindow(to);
        float sx = origin.getWidth() / (float) card.getWidth();
        float sy = origin.getHeight() / (float) card.getHeight();
        float tx = from[0] + origin.getWidth() / 2f
                - (to[0] + card.getLeft() + card.getWidth() / 2f);
        float ty = from[1] + origin.getHeight() / 2f
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
        blur(scroll, false);
        scroll.animate().alpha(1).setDuration(240).start();
        morph(layer.getChildAt(0), connections, false, () -> {
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

    private void showPicker(Item item, View origin) {
        if (popup != null || editor != null) return;
        source = origin;
        popup = new Picker(getContext(), controls, item, Math.min(width, dp(400)),
                this::closePicker);
        addView(popup, new LayoutParams(-1, -1));
        View behind = expanded != null ? expanded : scroll;
        blur(behind, true);
        behind.animate().alpha(0.2f).setDuration(240).start();
        popup.setAlpha(0);
        popup.animate().alpha(1).setDuration(180).start();
        View card = ((FrameLayout) popup).getChildAt(0);
        card.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                card.getViewTreeObserver().removeOnPreDrawListener(this);
                morph(card, origin, true, null);
                return true;
            }
        });
    }

    private void closePicker() {
        FrameLayout old = popup;
        if (old == null) return;
        popup = null;
        service.setKeyboard(false);
        if (expanded != null) {
            blur(expanded, false);
            expanded.animate().alpha(1).setDuration(220).start();
        } else {
            blur(scroll, false);
            scroll.animate().alpha(1).setDuration(220).start();
        }
        old.animate().alpha(0).setDuration(240).start();
        if (source != null && source.isAttachedToWindow()) {
            morph(old.getChildAt(0), source, false, () -> removeView(old));
        } else {
            postDelayed(() -> removeView(old), 250);
        }
        postDelayed(() -> refresh(true), 100);
    }

    /** Konten di belakang dibuat buram (Android 12+). */
    private void blur(View v, boolean on) {
        if (Build.VERSION.SDK_INT >= 31) {
            v.setRenderEffect(on
                    ? RenderEffect.createBlurEffect(dp(14), dp(14), Shader.TileMode.CLAMP)
                    : null);
        }
    }

    // ---------------------------------------------------------------- Gerakan

    @Override int pageDirection() { return 1; }

    @Override void move(float value, float shown) {
        float away = 1 - value;
        for (int i = 0; i < box.getChildCount(); i++) {
            // Tiap bagian bergerak sedikit berbeda sehingga terasa berurutan.
            box.getChildAt(i).setTranslationY(-away * dp(70 + i * 34));
        }
        float scale = 0.92f + 0.08f * shown;
        box.setPivotX(box.getWidth() / 2f);
        box.setPivotY(0);
        box.setScaleX(scale);
        box.setScaleY(scale);
    }

    @Override boolean back() {
        if (editor != null) { closeEditor(); return true; }
        if (popup != null) { closePicker(); return true; }
        if (expanded != null) { collapse(); return true; }
        return false;
    }

    @Override boolean canDrag() { return popup == null && expanded == null && editor == null; }
}
