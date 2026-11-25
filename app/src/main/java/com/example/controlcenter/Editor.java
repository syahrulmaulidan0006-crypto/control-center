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
    private final GridLayout used, spare;
    private final int columns, cell;
    private final TextView spareTitle;

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
        TextView hint = text("Ketuk untuk menyingkirkan. Tahan lalu geser untuk memindahkan.", 13);
        hint.setAlpha(0.7f);
        hint.setPadding(0, dp(6), 0, dp(14));
        page.addView(hint, new LinearLayout.LayoutParams(wide, -2));

        used = grid();
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
        for (Item item : order) addUsed(item);
        int count = 0;
        for (Item item : Item.values()) {
            if (item.movable() && !order.contains(item)) { addSpare(item); count++; }
        }
        spareTitle.setVisibility(count == 0 ? GONE : VISIBLE);
    }

    private FrameLayout badged(Item item, int icon, int color) {
        FrameLayout box = new FrameLayout(getContext());
        box.setClipChildren(false);
        int size = cell - dp(14);
        box.addView(new TileView(getContext(), item),
                new LayoutParams(size, size, Gravity.CENTER));
        ImageView badge = new ImageView(getContext());
        badge.setImageResource(icon);
        badge.setPadding(dp(3), dp(3), dp(3), dp(3));
        badge.setBackground(new Glass(getContext(), 12, color));
        LayoutParams p = new LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.START);
        p.setMargins(dp(4), dp(4), 0, 0);
        box.addView(badge, p);
        return box;
    }

    private void addUsed(Item item) {
        FrameLayout content = badged(item, R.drawable.ic_minus, 0xE6E0484E);
        FrameLayout holder = new FrameLayout(getContext()) {
            @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }
        };
        holder.setClipChildren(false);
        holder.setContentDescription("Singkirkan " + item.label);
        holder.addView(content, new LayoutParams(cell, cell));
        holder.setOnTouchListener(new Drag(item));
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = cell;
        p.height = cell;
        used.addView(holder, p);

        // Tombol bergoyang selama mode atur.
        ObjectAnimator shake = ObjectAnimator.ofFloat(content, View.ROTATION, -2.2f, 2.2f);
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
        box.addView(badged(item, R.drawable.ic_plus, 0xE62EAD63),
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
        private float x, y;
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
                    if (holding) drop(v, dx, dy);
                    else if (!moved) {
                        v.animate().scaleX(0.2f).scaleY(0.2f).alpha(0).setDuration(180)
                                .withEndAction(() -> { order.remove(item); changed(); }).start();
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(hold);
                    if (holding) rebuild();
                    break;
                default:
                    break;
            }
            return true;
        }

        private void drop(View v, float dx, float dy) {
            int column = (int) ((v.getLeft() + dx + cell / 2f) / cell);
            int row = (int) ((v.getTop() + dy + cell / 2f) / cell);
            column = Math.max(0, Math.min(columns - 1, column));
            int target = Math.max(0, Math.min(order.size() - 1,
                    Math.max(0, row) * columns + column));
            order.remove(item);
            order.add(target, item);
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
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
