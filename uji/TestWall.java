package com.example.controlcenter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;

/** Hanya untuk uji emulator: latar berwarna dan tajam, agar efek kaca terlihat. */
public class TestWall extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new Wall(this));
    }

    private static class Wall extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Wall(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFF0B3D91, 0xFF7B2FF7,
                    0xFFFF3D7F, 0xFFFFA53D, 0xFF12C2A9}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            int[] colors = {0xFFFFE14D, 0xFF35E0FF, 0xFFFFFFFF, 0xFF101840, 0xFF62F26B};
            for (int i = 0; i < 14; i++) {
                p.setColor(colors[i % colors.length]);
                c.drawCircle(w * ((i * 37) % 100) / 100f, h * ((i * 53 + 11) % 100) / 100f,
                        w * (0.05f + (i % 4) * 0.035f), p);
            }
            p.setColor(0xFFFFFFFF);
            p.setTextSize(w / 12);
            for (int i = 1; i < 12; i += 2) {
                c.drawText("Layar di belakang " + i, w / 20, h * i / 12, p);
            }
        }
    }
}
