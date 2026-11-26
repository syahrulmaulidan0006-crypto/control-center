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
    private final Controls controls;
    private final LinearLayout box, list;
    private final Set<String> open = new HashSet<>();
    private TileView torch, camera;
    /** Isi awal muncul mengikuti jari; hanya yang datang kemudian yang dianimasikan sendiri. */
    private boolean opened;
    /** Notifikasi yang sudah tampil, agar hanya yang baru yang dianimasikan. */
    private final Set<String> shown = new HashSet<>();
    private int arriving;

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
        build();
        // Kartu yang dibuat ulang saat panel masih bergerak mengikuti posisi sekarang.
        if (progress() < 1) setProgress(progress());
    };

    private void build() {
        list.removeAllViews();
        arriving = 0;
        if (!MusicListener.connected()) {
            empty("Akses notifikasi belum tersambung.\nKetuk untuk membuka pengaturan.",
                    () -> controls.launch(new android.content.Intent(
                            android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            return;
        }
        List<StatusBarNotification> all = MusicListener.list();
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
            if (items.size() == 1) { card(items.get(0), null, 0); continue; }
            if (open.contains(key)) {
                pill(list, "Ringkas " + Notes.app(getContext(), key), () -> {
                    open.remove(key);
                    for (StatusBarNotification n : items) shown.remove(n.getKey());
                    build();
                });
                for (StatusBarNotification n : items) card(n, null, 0);
            } else {
                card(items.get(0), () -> {
                    open.add(key);
                    for (StatusBarNotification n : items) shown.remove(n.getKey());
                    build();
                }, items.size() - 1);
            }
        }
    }

    private void card(StatusBarNotification n, Runnable stack, int hidden) {
        NoteCard c = new NoteCard(getContext(), n, 0x2AFFFFFF);
        c.setOnClickListener(v -> {
            if (stack != null) { stack.run(); return; }
            Notes.open(getContext(), n);
            service.closePanel();
        });
        if (n.isClearable()) {
            c.rightOnly = true;
            c.gestures(() -> {
                View whole = c.getParent() == list ? c : (View) c.getParent();
                Motion.collapse(whole, () -> MusicListener.dismiss(n.getKey()));
            }, null, null);
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        list.addView(item, p);
        if (shown.add(n.getKey()) && opened) Motion.enter(item, arriving++ * 55L, -dp(22));
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
                // Kartu keluar berurutan, baru kemudian dihapus.
                for (int i = 0; i < list.getChildCount(); i++) {
                    list.getChildAt(i).animate().translationX(getWidth()).alpha(0)
                            .setStartDelay(i * 45L).setDuration(260).start();
                }
                postDelayed(() -> {
                    for (StatusBarNotification n : clear) {
                        if (n.isClearable()) MusicListener.dismiss(n.getKey());
                    }
                }, list.getChildCount() * 45L + 270);
            });
        }
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void pill(LinearLayout parent, String label, Runnable click) {
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
        list.addView(holder, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextClock clock(String format, int size) {
        TextClock c = new TextClock(getContext());
        c.setFormat12Hour(format);
        c.setFormat24Hour(format);
        c.setTextSize(size);
        c.setTextColor(Color.WHITE);
        c.setGravity(Gravity.CENTER);
        c.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
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
            if (part != list) part.setTranslationX(offset * dp(24 + 26 * order++));
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).setTranslationX(offset * dp(24 + 26 * Math.min(order++, 7)));
        }
        torch.setTranslationX(offset * dp(60));
        camera.setTranslationX(offset * dp(140));
    }
}
