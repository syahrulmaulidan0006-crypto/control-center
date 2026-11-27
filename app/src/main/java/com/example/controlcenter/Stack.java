package com.example.controlcenter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** Tumpukan: tepi kartu-kartu di belakang mengintip dari bawah kartu teratas. */
class Stack extends FrameLayout {
    private static final int STEP = 9, SIDE = 13;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final int depth;

    Stack(Context context, View front, int hidden) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        depth = Math.min(2, hidden);
        setWillNotDraw(false);
        setClipChildren(false);
        // Tanpa ini kartu yang digeser terpotong di tepi tumpukan karena ada padding bawah.
        setClipToPadding(false);
        setPadding(0, 0, 0, Math.round(depth * STEP * d));
        addView(front, new LayoutParams(-1, -2));
    }

    @Override protected void onDraw(Canvas c) {
        View front = getChildAt(0);
        float radius = 28 * d;
        RectF above = new RectF(front.getLeft(), front.getTop(),
                front.getRight(), front.getBottom());
        c.save();
        Frost.begin(this);
        for (int i = 1; i <= depth; i++) {
            // Bagian yang tertutup lapisan di atasnya tidak digambar, agar kaca tidak keruh.
            Path covered = new Path();
            covered.addRoundRect(above, radius, radius, Path.Direction.CW);
            c.clipOutPath(covered);
            RectF layer = new RectF(front.getLeft() + i * SIDE * d,
                    front.getBottom() - 2 * radius + i * STEP * d,
                    front.getRight() - i * SIDE * d,
                    front.getBottom() + i * STEP * d);
            Glass.draw(c, paint, layer, radius, d, i == 1 ? 0x24FFFFFF : 0x12FFFFFF, true);
            above = layer;
        }
        Frost.end();
        c.restore();
    }
}
