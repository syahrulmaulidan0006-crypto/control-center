package com.example.controlcenter;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Layar atur tombol: singkirkan, tambahkan, dan pindahkan tombol. */
class Editor extends FrameLayout {
    private final List<Item> order;
    private final List<ObjectAnimator> shaking = new ArrayList<>();
    private final FrameLayout used;
    private final GridLayout spare;
    private final int columns, cell;
    private final TextView spareTitle;
    private Item fresh;

    Editor(Context context, int width, int columns, Runnable done) {
        super(context);
        this.columns = columns;
        cell = Math.min(width / columns, dp(96));
        order = Item.load(context);
        setBackgroundColor(0x80000000);
        setClickable(true);

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(0, dp(32), 0, dp(32));
        int wide = cell * columns;

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Atur tombol", 22), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(pill("Selesai", done));
        page.addView(head, new LinearLayout.LayoutParams(wide, -2));
        TextView hint = text("Ketuk tombol untuk mengubah ukuran, tanda minus untuk "
                + "menyingkirkan. Tahan lalu geser untuk memindahkan.", 13);
        hint.setAlpha(0.7f);
        hint.setPadding(0, dp(6), 0, dp(14));
        page.addView(hint, new LinearLayout.LayoutParams(wide, -2));

        used = new FrameLayout(context);
        used.setClipChildren(false);
        page.addView(used, new LinearLayout.LayoutParams(wide, -2));
        spareTitle = text("Tambah tombol", 17);
        spareTitle.setPadding(0, dp(20), 0, dp(10));
        page.addView(spareTitle, new LinearLayout.LayoutParams(wide, -2));
        spare = grid();
        page.addView(spare, new LinearLayout.LayoutParams(wide, -2));
        LinearLayout.LayoutParams reset = new LinearLayout.LayoutParams(-2, -2);
        reset.topMargin = dp(20);
        page.addView(pill("Kembalikan susunan awal", () -> {
            order.clear();
            order.addAll(Item.standard());
            for (Item item : Item.values()) Item.resize(getContext(), item, 0);
            changed();
        }), reset);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page);
        addView(scroll, new LayoutParams(-1, -1));
        rebuild();
    }

    private void changed() {
        Item.save(getContext(), order);
        rebuild();
    }

    private void rebuild() {
        stop();
        used.removeAllViews();
        spare.removeAllViews();
        Pack pack = new Pack(columns, 0);
        for (Item item : order) addUsed(item, pack);
        int count = 0;
        for (Item item : Item.values()) {
            if (item.movable() && !order.contains(item)) { addSpare(item); count++; }
        }
        spareTitle.setVisibility(count == 0 ? GONE : VISIBLE);
    }

    private FrameLayout badged(Item item, int icon, int color, int wide, int high,
            boolean large) {
        FrameLayout box = new FrameLayout(getContext());
        box.setClipChildren(false);
        TileView tile = new TileView(getContext(), item);
        tile.large = large;
        box.addView(tile, new LayoutParams(wide - dp(14), high - dp(14), Gravity.CENTER));
        box.addView(badge(icon, color), corner(Gravity.TOP | Gravity.START));
        return box;
    }

    private ImageView badge(int icon, int color) {
        ImageView badge = new ImageView(getContext());
        badge.setImageResource(icon);
        badge.setPadding(dp(3), dp(3), dp(3), dp(3));
        badge.setBackground(new Glass(getContext(), 12, color));
        return badge;
    }

    private LayoutParams corner(int gravity) {
        LayoutParams p = new LayoutParams(dp(22), dp(22), gravity);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private void addUsed(Item item, Pack pack) {
        int size = Item.size(getContext(), item);
        int w = Item.SPANS[size][0], h = Item.SPANS[size][1];
        int[] at = pack.place(w, h);
        FrameLayout content = badged(item, R.drawable.ic_minus, 0xE6E0484E, w * cell, h * cell,
                size == 2);
        content.addView(badge(R.drawable.ic_resize, 0xE62F6FE0),
                corner(Gravity.BOTTOM | Gravity.END));
        FrameLayout holder = new FrameLayout(getContext()) {
            @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }
        };
        holder.setClipChildren(false);
        holder.setTag(item);
        holder.setContentDescription("Atur " + item.label);
        holder.addView(content, new LayoutParams(w * cell, h * cell));
        holder.setOnTouchListener(new Drag(item));
        if (item == fresh) {
            fresh = null;
            Motion.enter(holder, 0, dp(24));
        }
        LayoutParams p = new LayoutParams(w * cell, h * cell, Gravity.TOP | Gravity.LEFT);
        p.leftMargin = at[0] * cell;
        p.topMargin = at[1] * cell;
        used.addView(holder, p);

        // Tombol bergoyang selama mode atur; yang besar lebih pelan goyangnya.
        float swing = 2.2f / Math.max(w, h);
        ObjectAnimator shake = ObjectAnimator.ofFloat(content, View.ROTATION, -swing, swing);
        shake.setDuration(130);
        shake.setRepeatCount(ValueAnimator.INFINITE);
        shake.setRepeatMode(ValueAnimator.REVERSE);
        shake.setStartDelay(used.getChildCount() * 37L % 130);
        shake.start();
        shaking.add(shake);
    }

    private void addSpare(Item item) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.addView(badged(item, R.drawable.ic_plus, 0xE62EAD63, cell, cell, false),
                new LinearLayout.LayoutParams(cell, cell));
        TextView name = text(item.label, 11);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        box.addView(name, new LinearLayout.LayoutParams(cell, -2));
        // Tombol di dalamnya tidak menerima sentuhan sendiri.
        View cover = new View(getContext());
        cover.setContentDescription("Tambahkan " + item.label);
        FrameLayout holder = new FrameLayout(getContext());
        holder.addView(box, new LayoutParams(cell, -2));
        holder.addView(cover, new LayoutParams(cell, cell + dp(30)));
        cover.setOnClickListener(v -> box.animate().scaleX(0.3f).scaleY(0.3f).alpha(0)
                .setDuration(180).withEndAction(() -> {
                    if (!order.contains(item)) order.add(item);
                    fresh = item;
                    changed();
                }).start());
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = cell;
        p.height = cell + dp(34);
        spare.addView(holder, p);
    }

    /** Ketuk menyingkirkan; tahan lalu geser memindahkan. */
    private class Drag implements OnTouchListener {
        private final Item item;
        private View view;
        private float x, y, localX, localY;
        private boolean holding, moved;
        private final Runnable hold = () -> {
            holding = true;
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            view.getParent().requestDisallowInterceptTouchEvent(true);
            view.setTranslationZ(dp(12));
            view.animate().scaleX(1.18f).scaleY(1.18f).setDuration(140).start();
        };

        Drag(Item item) { this.item = item; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            view = v;
            float dx = e.getRawX() - x, dy = e.getRawY() - y;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x = e.getRawX();
                    y = e.getRawY();
                    localX = e.getX();
                    localY = e.getY();
                    holding = moved = false;
                    v.postDelayed(hold, 220);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (holding) {
                        v.setTranslationX(dx);
                        v.setTranslationY(dy);
                    } else if (Math.hypot(dx, dy)
                            > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                        moved = true;
                        v.removeCallbacks(hold);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(hold);
                    if (holding) {
                        // Susunan diubah setelah sentuhan selesai diproses. Mengubahnya di
                        // sini membuat aplikasi berhenti.
                        holding = false;
                        Editor.this.post(() -> drop(v, dx, dy));
                    } else if (!moved && localX < dp(36) && localY < dp(36)) {
                        v.animate().scaleX(0.2f).scaleY(0.2f).alpha(0).setDuration(180)
                                .withEndAction(() -> { order.remove(item); changed(); }).start();
                    } else if (!moved) {
                        // Ketuk badan tombol: kecil, lebar, besar, lalu kecil lagi.
                        Haptic.click(getContext());
                        Item.resize(getContext(), item,
                                (Item.size(getContext(), item) + 1) % Item.SPANS.length);
                        fresh = item;
                        Editor.this.post(Editor.this::rebuild);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(hold);
                    if (holding) {
                        holding = false;
                        Editor.this.post(Editor.this::rebuild);
                    }
                    break;
                default:
                    break;
            }
            return true;
        }

        /** Pindah ke tempat tombol yang pusatnya paling dekat dengan titik lepas. */
        private void drop(View v, float dx, float dy) {
            float cx = v.getLeft() + dx + v.getWidth() / 2f;
            float cy = v.getTop() + dy + v.getHeight() / 2f;
            Item nearest = item;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < used.getChildCount(); i++) {
                View other = used.getChildAt(i);
                double far = Math.hypot(other.getLeft() + other.getWidth() / 2f - cx,
                        other.getTop() + other.getHeight() / 2f - cy);
                if (far < best) {
                    best = far;
                    nearest = (Item) other.getTag();
                }
            }
            int to = order.indexOf(nearest);
            order.remove(item);
            order.add(Math.max(0, Math.min(order.size(), to)), item);
            changed();
        }
    }

    private void stop() {
        for (ObjectAnimator a : shaking) a.cancel();
        shaking.clear();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private GridLayout grid() {
        GridLayout g = new GridLayout(getContext());
        g.setColumnCount(columns);
        g.setClipChildren(false);
        return g;
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        return t;
    }

    private TextView pill(String value, Runnable click) {
        TextView t = text(value, 14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setBackground(new Glass(getContext(), 22, 0x33FFFFFF));
        t.setOnClickListener(v -> click.run());
        Motion.press(t);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
