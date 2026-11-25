package com.example.controlcenter;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.View;

/** Baris status: sinyal, operator, Wi-Fi, jenis internet, dan baterai. */
class StatusRow extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private String carrier = "", network = "";
    private int signal, wifi = -1, battery;
    private boolean charging, airplane;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            read();
            postDelayed(this, 2000);
        }
    };

    StatusRow(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        read();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(poll);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(poll);
        super.onDetachedFromWindow();
    }

    private void read() {
        Context c = getContext();
        try {
            Intent b = c.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                battery = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100
                        / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            }
            airplane = Settings.Global.getInt(c.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
            TelephonyManager phone = c.getSystemService(TelephonyManager.class);
            carrier = airplane ? "Mode pesawat" : phone.getNetworkOperatorName();
            SignalStrength s = phone.getSignalStrength();
            signal = airplane || s == null ? 0 : s.getLevel();
            ConnectivityManager net = c.getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = net.getNetworkCapabilities(net.getActiveNetwork());
            wifi = -1;
            network = "Tanpa internet";
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    int rssi = c.getApplicationContext().getSystemService(WifiManager.class)
                            .getConnectionInfo().getRssi();
                    wifi = WifiManager.calculateSignalLevel(rssi, 3);
                    network = "";
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    network = "Data";
                } else {
                    network = "Internet";
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    network = "Tanpa internet";
                }
            }
        } catch (Exception ignored) { }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float h = getHeight(), mid = h / 2;
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(13 * d);
        paint.setFakeBoldText(true);
        float baseline = mid - (paint.ascent() + paint.descent()) / 2;

        // Kiri: batang sinyal, operator, Wi-Fi.
        float x = 4 * d;
        for (int i = 0; i < 4; i++) {
            paint.setColor(i < signal ? 0xFFFFFFFF : 0x59FFFFFF);
            float top = mid + 6 * d - (4 + i * 2.7f) * d;
            c.drawRoundRect(x, top, x + 3 * d, mid + 6 * d, d, d, paint);
            x += 4.5f * d;
        }
        x += 5 * d;
        paint.setColor(0xFFFFFFFF);
        String name = carrier.length() > 16 ? carrier.substring(0, 15) + "." : carrier;
        c.drawText(name, x, baseline, paint);
        x += paint.measureText(name) + 7 * d;
        if (wifi >= 0) {
            int res = wifi == 0 ? R.drawable.ic_wifi1 : wifi == 1 ? R.drawable.ic_wifi2
                    : R.drawable.ic_wifi;
            Drawable icon = getContext().getDrawable(res);
            icon.setBounds((int) x, (int) (mid - 8 * d), (int) (x + 16 * d),
                    (int) (mid + 8 * d));
            icon.draw(c);
            x += 22 * d;
        }
        if (!network.isEmpty()) c.drawText(network, x, baseline, paint);

        // Kanan: persentase dan ikon baterai.
        float right = getWidth() - 4 * d;
        c.drawRoundRect(right - 2.5f * d, mid - 2.5f * d, right, mid + 2.5f * d, d, d, paint);
        RectF body = new RectF(right - 30 * d, mid - 7 * d, right - 4 * d, mid + 7 * d);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f * d);
        paint.setColor(0xB3FFFFFF);
        c.drawRoundRect(body, 4 * d, 4 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(charging ? 0xFF37D98A : battery <= 15 ? 0xFFFF5C5C : 0xFFFFFFFF);
        float full = body.width() - 4 * d;
        c.drawRoundRect(body.left + 2 * d, body.top + 2 * d,
                body.left + 2 * d + full * battery / 100f, body.bottom - 2 * d,
                2 * d, 2 * d, paint);
        paint.setColor(0xFFFFFFFF);
        String level = (charging ? "Mengisi " : "") + battery + "%";
        c.drawText(level, body.left - 6 * d - paint.measureText(level), baseline, paint);
        setContentDescription(name + " " + network + ", baterai " + level);
    }
}
