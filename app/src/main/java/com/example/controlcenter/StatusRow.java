package com.example.controlcenter;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;

/** Baris status: sinyal, operator, Wi-Fi, jenis internet, dan baterai. */
class StatusRow extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private String carrier = "", network = "";
    private int signal, wifi = -1, battery;
    private boolean charging, airplane, locked, located;
    private float fill;

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
            locked = Settings.System.getInt(c.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 1) == 0;
            located = c.getSystemService(LocationManager.class).isLocationEnabled();
            TelephonyManager phone = c.getSystemService(TelephonyManager.class);
            // Pada HP dua SIM, tampilkan SIM yang dipakai untuk data.
            int sim = SubscriptionManager.getDefaultDataSubscriptionId();
            if (sim != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                phone = phone.createForSubscriptionId(sim);
            }
            carrier = airplane ? "Mode pesawat" : operator(phone);
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

    private static String operator(TelephonyManager phone) {
        String name = phone.getNetworkOperatorName();
        if (name == null || name.isEmpty()) name = phone.getSimOperatorName();
        if (name != null && !name.isEmpty()) return name;
        return phone.getSimState() == TelephonyManager.SIM_STATE_ABSENT
                ? "Tanpa SIM" : "Seluler";
    }

    private float symbol(Canvas c, int res, float right, float size) {
        Drawable icon = getContext().getDrawable(res).mutate();
        float mid = getHeight() / 2f;
        icon.setBounds((int) (right - size), (int) (mid - size / 2), (int) right,
                (int) (mid + size / 2));
        icon.draw(c);
        return right - size - 6 * d;
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

        // Isi baterai bergerak halus menuju nilai sebenarnya.
        fill += (battery - fill) * 0.12f;
        if (Math.abs(battery - fill) > 0.5f) postInvalidateOnAnimation();
        else fill = battery;
        // Kanan: lokasi, kunci rotasi, persentase, dan baterai berbentuk pil.
        float right = getWidth() - 2 * d;
        paint.setColor(0x80FFFFFF);
        c.drawRoundRect(right - 2 * d, mid - 2.5f * d, right, mid + 2.5f * d, d, d, paint);
        RectF body = new RectF(right - 30 * d, mid - 7 * d, right - 3 * d, mid + 7 * d);
        paint.setColor(0x59FFFFFF);
        c.drawRoundRect(body, 4.5f * d, 4.5f * d, paint);
        int color = charging ? 0xFF37D98A : battery <= 20 ? 0xFFFF5C5C : 0xFFFFFFFF;
        paint.setColor(color);
        c.save();
        c.clipRect(body.left, body.top, body.left + body.width() * fill / 100f, body.bottom);
        c.drawRoundRect(body, 4.5f * d, 4.5f * d, paint);
        c.restore();
        if (charging) {
            Path bolt = new Path();
            float bx = body.centerX(), u = d;
            bolt.moveTo(bx + 1.5f * u, mid - 6 * u);
            bolt.lineTo(bx - 4 * u, mid + u);
            bolt.lineTo(bx - 0.5f * u, mid + u);
            bolt.lineTo(bx - 1.5f * u, mid + 6 * u);
            bolt.lineTo(bx + 4 * u, mid - u);
            bolt.lineTo(bx + 0.5f * u, mid - u);
            bolt.close();
            paint.setColor(0xFF10261B);
            c.drawPath(bolt, paint);
        }
        paint.setColor(0xFFFFFFFF);
        String level = battery + "%";
        float text = body.left - 6 * d - paint.measureText(level);
        c.drawText(level, text, baseline, paint);
        float next = text - 7 * d;
        if (locked) next = symbol(c, R.drawable.ic_rotation, next, 15 * d);
        if (located) symbol(c, R.drawable.ic_arrow, next, 12 * d);
        setContentDescription(name + " " + network + ", baterai " + level
                + (charging ? ", mengisi daya" : ""));
    }
}
