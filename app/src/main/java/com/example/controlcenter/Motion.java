package com.example.controlcenter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Gerakan kecil yang dipakai bersama agar semua bagian terasa sama. */
final class Motion {
    /** Mengecil saat disentuh, lalu memantul saat dilepas. Klik tetap bekerja. */
    static void press(View view) {
        view.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.93f).scaleY(0.93f).setStartDelay(0).setDuration(110).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1).scaleY(1).setStartDelay(0).setDuration(340)
                        .setInterpolator(new OvershootInterpolator(3f)).start();
            }
            return false;
        });
    }

    /** Muncul memudar sambil bergeser ke tempatnya. */
    static void enter(View v, long delay, float fromY) {
        v.setAlpha(0);
        v.setTranslationY(fromY);
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setStartDelay(delay)
                .setDuration(330).setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    /** Tinggi menyusut sampai hilang sehingga kartu di bawahnya naik dengan halus. */
    static void collapse(View v, Runnable end) {
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        int height = v.getHeight(), top = p.topMargin;
        ValueAnimator a = ValueAnimator.ofFloat(1, 0);
        a.setDuration(240);
        a.setInterpolator(new DecelerateInterpolator(1.3f));
        a.addUpdateListener(x -> {
            float f = (float) x.getAnimatedValue();
            p.height = Math.max(1, Math.round(height * f));
            p.topMargin = Math.round(top * f);
            v.setAlpha(f);
            v.setLayoutParams(p);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                v.setVisibility(View.GONE);
                end.run();
            }
        });
        a.start();
    }
}
