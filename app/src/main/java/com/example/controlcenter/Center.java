package com.example.controlcenter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pusat notifikasi: jam besar dan kartu yang ditumpuk per aplikasi. */
class Center extends Sheet {
    /** Sedikit melewati tujuan lalu kembali, seperti pegas. */
    private static final android.view.animation.Interpolator SPRING =
            new android.view.animation.PathInterpolator(0.22f, 1.12f, 0.36f, 1f);
    private final Controls controls;
    private final LinearLayout box, list;
    private final Set<String> open = new HashSet<>();
    private TileView torch, camera;
    /** Isi awal muncul mengikuti jari; hanya yang datang kemudian yang dianimasikan sendiri. */
    private boolean opened;
    /** Sudah dibuang pengguna tetapi belum dihapus sistem. */
    private final Set<String> gone = new HashSet<>();
    /** Kartu sedang digeser atau sedang dibersihkan: penyusunan ulang ditunda. */
    private boolean busy;
    /** Posisi bagian daftar sebelum disusun ulang, menunggu tata letak baru. */
    private Map<String, Spot> pending;

    private static class Spot {
        View view;
        float y, alpha, scale;
        /** Kunci notifikasi teratas, untuk kartu. */
        String front;
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            torch.setOn(controls.isOn(Item.TORCH), true);
            postDelayed(this, 600);
        }
    };

    Center(OverlayService service, Controls controls) {
        super(service);
        this.controls = controls;
        android.util.DisplayMetrics screen = getResources().getDisplayMetrics();
        boolean land = screen.widthPixels > screen.heightPixels;
        int width = land ? Math.min(screen.widthPixels - dp(200), dp(720))
                : Math.min(screen.widthPixels - dp(28), dp(430));

        box = new LinearLayout(service);
        box.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout clocks = box;
        if (land) {
            clocks = new LinearLayout(service);
            clocks.setOrientation(LinearLayout.VERTICAL);
            box.addView(clocks, new LinearLayout.LayoutParams(0, -2, 2));
        }

        TextClock date = clock("EEEE, d MMMM", 16);
        clocks.addView(date);
        TextClock time = clock("HH:mm", land ? 60 : 82);
        time.setFormat12Hour("h:mm");
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clocks.addView(time);

        list = new LinearLayout(service);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list, land ? new LinearLayout.LayoutParams(0, -2, 3)
                : new LinearLayout.LayoutParams(-1, -2));

        // Bagian yang sedang meluncur masuk tidak boleh terpotong tepi wadahnya.
        box.setClipChildren(false);
        clocks.setClipChildren(false);
        list.setClipChildren(false);
        FrameLayout wrap = new FrameLayout(service);
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);
        wrap.setPadding(0, dp(land ? 20 : 48), 0, dp(land ? 30 : 120));
        wrap.addView(box, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER_HORIZONTAL));
        wrap.setOnClickListener(v -> service.closePanel());
        scroll = new ScrollView(service);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(wrap);
        addView(scroll, new LayoutParams(-1, -1));

        torch = new TileView(service, Item.TORCH);
        torch.setOnClickListener(v -> controls.click(Item.TORCH));
        torch.setOn(controls.isOn(Item.TORCH), false);
        addView(torch, corner(Gravity.START));
        camera = new TileView(service, Item.CAMERA);
        camera.setOnClickListener(v -> controls.click(Item.CAMERA));
        addView(camera, corner(Gravity.END));
        build();
        opened = true;
    }

    private LayoutParams corner(int side) {
        LayoutParams p = new LayoutParams(dp(60), dp(60), Gravity.BOTTOM | side);
        p.setMargins(dp(34), 0, dp(34), dp(36));
        return p;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MusicListener.observe(() -> {
            removeCallbacks(rebuild);
            postDelayed(rebuild, 150);
        });
        post(poll);
    }

    @Override protected void onDetachedFromWindow() {
        MusicListener.observe(null);
        removeCallbacks(poll);
        removeCallbacks(rebuild);
        super.onDetachedFromWindow();
    }

    private boolean replying;
    private final Runnable rebuild = () -> {
        if (replying) return;
        if (busy) { postRebuild(250); return; }
        build();
        // Kartu yang dibuat ulang saat panel masih bergerak mengikuti posisi sekarang.
        if (progress() < 1) setProgress(progress());
    };

    private void postRebuild(long delay) {
        removeCallbacks(rebuild);
        postDelayed(rebuild, delay);
    }

    /** Menyusun ulang daftar; bagian yang berpindah meluncur dari tempat lamanya. */
    private void build() {
        boolean animate = opened && progress() >= 1 && list.isLaidOut();
        boolean waiting = pending != null;
        if (animate && !waiting) pending = capture();
        fillList();
        if (!animate || waiting) return;
        list.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                list.getViewTreeObserver().removeOnPreDrawListener(this);
                Map<String, Spot> before = pending;
                pending = null;
                if (before != null) glide(before);
                return true;
            }
        });
        invalidate();
    }

    private Map<String, Spot> capture() {
        Map<String, Spot> spots = new LinkedHashMap<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            Spot s = new Spot();
            s.view = v;
            s.y = v.getTop() + v.getTranslationY();
            s.alpha = v.getAlpha();
            s.scale = v.getScaleX();
            s.front = (String) v.getTag(R.id.front);
            spots.put((String) v.getTag(), s);
        }
        return spots;
    }

    private void glide(Map<String, Spot> before) {
        Set<Spot> used = new HashSet<>();
        int arriving = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            String tag = (String) v.getTag(), front = (String) v.getTag(R.id.front);
            Spot s = before.get(tag);
            if (s == null && front != null) {
                // Tumpukan dibuka atau diringkas: berangkat dari tempat kartu yang sama.
                s = before.get(front);
                if (s == null) {
                    for (Spot other : before.values()) {
                        if (front.equals(other.front)) { s = other; break; }
                    }
                }
            }
            if (s == null) {
                if ("empty".equals(tag)) welcome((LinearLayout) v);
                else Motion.enter(v, arriving++ * 55L, -dp(22));
                continue;
            }
            used.add(s);
            v.setTranslationY(s.y - v.getTop());
            v.setAlpha(s.alpha);
            v.setScaleX(s.scale);
            v.setScaleY(s.scale);
            v.animate().translationY(0).alpha(1).scaleX(1).scaleY(1).setStartDelay(0)
                    .setDuration(460).setInterpolator(SPRING).start();
            if (tag.startsWith("g:") && s.front != null && !s.front.equals(front)
                    && v instanceof SwipeRow) {
                rise(((SwipeRow) v).card);
            }
        }
        // Yang tidak ada lagi memudar di tempatnya, tidak hilang tiba-tiba.
        for (Spot s : before.values()) {
            View v = s.view;
            if (used.contains(s) || v.getParent() != null || s.alpha < 0.05f
                    || "empty".equals(v.getTag())) continue;
            list.getOverlay().add(v);
            v.animate().alpha(0).scaleX(0.88f).scaleY(0.88f).setStartDelay(0).setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> list.getOverlay().remove(v)).start();
        }
    }

    /** Kartu berikutnya naik dari dalam tumpukan. */
    private void rise(View card) {
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.setTranslationY(dp(16));
        card.setAlpha(0.15f);
        card.animate().scaleX(1).scaleY(1).translationY(0).alpha(1).setStartDelay(40)
                .setDuration(420).setInterpolator(SPRING).start();
    }

    /** Daftar kosong: lonceng membesar lalu berayun pelan. */
    private void welcome(LinearLayout holder) {
        holder.setAlpha(0);
        holder.setScaleX(0.7f);
        holder.setScaleY(0.7f);
        holder.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(180).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        View bell = holder.getChildAt(0);
        android.animation.ObjectAnimator swing = android.animation.ObjectAnimator.ofFloat(
                bell, View.ROTATION, 0, -18, 14, -9, 5, 0);
        swing.setStartDelay(420);
        swing.setDuration(720);
        swing.start();
    }

    private void fillList() {
        list.removeAllViews();
        if (!MusicListener.connected()) {
            empty("Akses notifikasi belum tersambung.\nKetuk untuk membuka pengaturan.",
                    () -> controls.launch(new android.content.Intent(
                            android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            return;
        }
        List<StatusBarNotification> all = MusicListener.list();
        Set<String> present = new HashSet<>();
        for (StatusBarNotification n : all) present.add(n.getKey());
        gone.retainAll(present);
        all.removeIf(n -> gone.contains(n.getKey()));
        if (all.isEmpty()) { empty("Tidak ada notifikasi", null); return; }
        long hour = System.currentTimeMillis() - 3600_000;
        Map<String, List<StatusBarNotification>> fresh = new LinkedHashMap<>();
        Map<String, List<StatusBarNotification>> older = new LinkedHashMap<>();
        for (StatusBarNotification n : all) {
            Map<String, List<StatusBarNotification>> target =
                    fresh.containsKey(n.getPackageName()) || n.getPostTime() >= hour
                            ? fresh : older;
            target.computeIfAbsent(n.getPackageName(), k -> new ArrayList<>()).add(n);
        }
        boolean first = true;
        if (!fresh.isEmpty()) { section("Baru", all); groups(fresh); first = false; }
        if (!older.isEmpty()) { section("Sebelumnya", first ? all : null); groups(older); }
    }

    private void groups(Map<String, List<StatusBarNotification>> map) {
        for (Map.Entry<String, List<StatusBarNotification>> g : map.entrySet()) {
            List<StatusBarNotification> items = g.getValue();
            String key = g.getKey();
            if (items.size() == 1) { card("g:" + key, items.get(0), null, 0); continue; }
            if (open.contains(key)) {
                pill(list, "Ringkas " + Notes.app(getContext(), key), () -> {
                    open.remove(key);
                    build();
                }).setTag("p:" + key);
                for (StatusBarNotification n : items) card(n.getKey(), n, null, 0);
            } else {
                card("g:" + key, items.get(0), () -> {
                    open.add(key);
                    build();
                }, items.size() - 1);
            }
        }
    }

    private void card(String tag, StatusBarNotification n, Runnable stack, int hidden) {
        NoteCard c = new NoteCard(getContext(), n, 0x2AFFFFFF);
        c.setOnClickListener(v -> {
            if (stack != null) { stack.run(); return; }
            Notes.open(getContext(), n);
            service.closePanel();
        });
        if (n.isClearable()) {
            c.rightOnly = true;
            c.held = value -> busy = value;
            c.gestures(() -> discard(n.getKey()), null, null);
        }
        c.actions(n, () -> {
            replying = true;
            service.setKeyboard(true);
        }, () -> {
            replying = false;
            service.setKeyboard(false);
            postDelayed(rebuild, 300);
        });
        View item = c;
        if (hidden > 0) {
            c.more("+" + hidden + " notifikasi lagi");
            item = new Stack(getContext(), c, hidden);
        }
        if (n.isClearable()) item = new SwipeRow(getContext(), item, c);
        item.setTag(tag);
        item.setTag(R.id.front, n.getKey());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        list.addView(item, p);
    }

    /** Daftar langsung dirapikan tanpa menunggu sistem selesai menghapus. */
    private void discard(String key) {
        busy = false;
        gone.add(key);
        build();
        MusicListener.dismiss(key);
        // Bila sistem menolak menghapus, kartunya dimunculkan lagi.
        postDelayed(() -> { if (gone.remove(key)) postRebuild(0); }, 2500);
    }

    private void section(String name, List<StatusBarNotification> clear) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(14), 0, 0);
        TextView t = text(name, 20, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (clear != null) {
            pill(row, "Hapus semua", () -> {
                if (busy) return;
                busy = true;
                Haptic.click(getContext());
                // Kartu keluar berurutan, baru kemudian dihapus.
                int order = 0;
                for (int i = 0; i < list.getChildCount(); i++) {
                    View v = list.getChildAt(i);
                    if (!(v instanceof SwipeRow)) continue;
                    v.animate().translationX(getWidth()).alpha(0).scaleX(0.86f).scaleY(0.86f)
                            .setStartDelay(Math.min(order++, 8) * 60L).setDuration(300)
                            .setInterpolator(
                                    new android.view.animation.AccelerateInterpolator(1.3f))
                            .start();
                }
                postDelayed(() -> {
                    busy = false;
                    List<String> keys = new ArrayList<>();
                    for (StatusBarNotification n : clear) {
                        if (n.isClearable()) keys.add(n.getKey());
                    }
                    gone.addAll(keys);
                    build();
                    for (String key : keys) MusicListener.dismiss(key);
                    postDelayed(() -> {
                        if (gone.removeAll(keys)) postRebuild(0);
                    }, 2500);
                }, Math.min(order, 8) * 60L + 300);
            });
        }
        row.setTag("s:" + name);
        row.setClipChildren(false);
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private View pill(LinearLayout parent, String label, Runnable click) {
        TextView t = text(label, 13, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), 0, dp(18), 0);
        t.setBackground(new Glass(getContext(), 22, 0x22FFFFFF));
        t.setOnClickListener(v -> click.run());
        Motion.press(t);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
        p.topMargin = parent == list ? dp(10) : 0;
        p.gravity = Gravity.END;
        parent.addView(t, p);
        return t;
    }

    private void empty(String message, Runnable click) {
        LinearLayout holder = new LinearLayout(getContext());
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(dp(20), dp(60), dp(20), dp(40));
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(R.drawable.ic_bell);
        icon.setPadding(dp(20), dp(20), dp(20), dp(20));
        icon.setBackground(new Glass(getContext(), 40, 0x22FFFFFF));
        holder.addView(icon, new LinearLayout.LayoutParams(dp(80), dp(80)));
        TextView t = text(message, 15, 0xD9FFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, 0);
        holder.addView(t);
        if (click != null) holder.setOnClickListener(v -> click.run());
        holder.setTag("empty");
        list.addView(holder, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextClock clock(String format, int size) {
        TextClock c = new TextClock(getContext());
        c.setFormat12Hour(format);
        c.setFormat24Hour(format);
        c.setTextSize(size);
        c.setTextColor(Color.WHITE);
        c.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
        c.setGravity(Gravity.CENTER);
        c.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        return t;
    }

    @Override int pageDirection() { return -1; }

    /** Jam, tiap kartu, dan tombol sudut muncul berurutan mengikuti jari. */
    @Override void move(float value, float shown) {
        int order = 0;
        for (int i = 0; i < box.getChildCount(); i++) {
            View part = box.getChildAt(i);
            if (part != list) reveal(part, value, shown, order++);
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            reveal(list.getChildAt(i), value, shown, order++);
        }
        for (View corner : new View[]{torch, camera}) {
            if (corner == null) continue;
            corner.setTranslationY((1 - value) * dp(150));
            corner.setScaleX(0.5f + 0.5f * shown);
            corner.setScaleY(0.5f + 0.5f * shown);
        }
    }

    private void reveal(View v, float value, float shown, int order) {
        float wait = Math.min(order, 7) * 0.07f;
        float f = Math.max(0, (shown - wait) / (1 - wait));
        // value melewati 1 sebentar di akhir sehingga terasa memantul.
        float bounce = Math.max(0, value - 1) * dp(90);
        v.setTranslationY(-(1 - f) * dp(70 + Math.min(order, 7) * 22) + bounce);
        v.setScaleX(0.86f + 0.14f * f);
        v.setScaleY(0.86f + 0.14f * f);
        v.setAlpha(f);
    }

    @Override void parallax(float offset) {
        int order = 0;
        for (int i = 0; i < box.getChildCount(); i++) {
            View part = box.getChildAt(i);
            if (part != list) part.setTranslationX(offset * dp(4 + 5 * order++));
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).setTranslationX(offset * dp(4 + 5 * Math.min(order++, 7)));
        }
        torch.setTranslationX(offset * dp(10));
        camera.setTranslationX(offset * dp(24));
    }
}
