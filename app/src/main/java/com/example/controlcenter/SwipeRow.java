package com.example.controlcenter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

/** Baris notifikasi: kapsul "Hapus" muncul di ruang yang ditinggalkan kartu saat digeser. */
class SwipeRow extends FrameLayout {
    final NoteCard card;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;
    private final float d;
    private boolean armed;
    /** Ikon membesar sesaat ketika geseran sudah cukup jauh. */
    private float pop;

    SwipeRow(Context context, View item, NoteCard card) {
        super(context);
        this.card = card;
        d = context.getResources().getDisplayMetrics().density;
        icon = context.getDrawable(R.drawable.ic_trash).mutate();
        setWillNotDraw(false);
        setClipChildren(false);
        addView(item, new LayoutParams(-1, -2));
        card.shift = this::shifted;
    }

    private void shifted() {
        boolean now = card.getTranslationX() > card.getWidth() * NoteCard.FAR;
        if (now != armed) {
            armed = now;
            if (now) Haptic.tick(getContext());
            android.animation.ValueAnimator a =
                    android.animation.ValueAnimator.ofFloat(pop, now ? 1 : 0);
            a.setDuration(260);
            a.setInterpolator(new android.view.animation.OvershootInterpolator(3f));
            a.addUpdateListener(x -> {
                pop = (float) x.getAnimatedValue();
                invalidate();
            });
            a.start();
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float x = card.getTranslationX(), height = card.getHeight();
        float wide = Math.min(x - 8 * d, getWidth());
        if (wide < 6 * d || height <= 0) return;
        // Muncul bertahap, lalu ikut memudar ketika kartunya terbang keluar.
        float seen = Math.min(1, wide / (56 * d)) * Math.min(1, card.getAlpha() * 1.8f);
        if (seen <= 0.01f) return;
        RectF area = new RectF(0, 0, wide, height);
        c.saveLayerAlpha(area, Math.round(255 * seen));
        float radius = Math.min(28 * d, Math.min(wide, height) / 2);
        Glass.draw(c, paint, area, radius, d, armed ? 0xD9FF453A : 0x80FF453A);
        float half = 12 * d * (0.6f + 0.4f * Math.min(1, wide / (70 * d))) * (1 + 0.22f * pop);
        float cx = wide / 2, cy = height / 2;
        icon.setBounds(Math.round(cx - half), Math.round(cy - half),
                Math.round(cx + half), Math.round(cy + half));
        icon.draw(c);
        c.restore();
    }
}
