package com.example.controlcenter;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.Toast;

/** Membaca status dan menjalankan aksi tiap tombol. */
class Controls {
    private final OverlayService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    final AudioManager audio;
    private final CameraManager camera;
    private String torchId;
    private boolean torchOn;

    private final CameraManager.TorchCallback torch = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean enabled) {
            if (id.equals(torchId)) torchOn = enabled;
        }
    };

    Controls(OverlayService service) {
        this.service = service;
        audio = service.getSystemService(AudioManager.class);
        camera = service.getSystemService(CameraManager.class);
        try {
            for (String id : camera.getCameraIdList()) {
                if (Boolean.TRUE.equals(camera.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                    torchId = id;
                    break;
                }
            }
            camera.registerTorchCallback(torch, handler);
        } catch (Exception ignored) { }
    }

    void release() { camera.unregisterTorchCallback(torch); }

    private int global(String key) {
        return Settings.Global.getInt(service.getContentResolver(), key, 0);
    }

    private int system(String key, int fallback) {
        return Settings.System.getInt(service.getContentResolver(), key, fallback);
    }

    boolean isOn(Item item) {
        try {
            switch (item) {
                case AIRPLANE: return global(Settings.Global.AIRPLANE_MODE_ON) == 1;
                case WIFI: return service.getApplicationContext()
                        .getSystemService(WifiManager.class).isWifiEnabled();
                case DATA: return service.getSystemService(TelephonyManager.class).isDataEnabled();
                case BLUETOOTH: return global("bluetooth_on") == 1;
                case LOCATION:
                    return service.getSystemService(LocationManager.class).isLocationEnabled();
                case ROTATION: return system(Settings.System.ACCELEROMETER_ROTATION, 1) == 0;
                case DND: return dndOn();
                case TORCH: return torchOn;
                case VIBRATE: return audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
                case DARK: return (service.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                        || service.getSystemService(UiModeManager.class).getNightMode()
                        == UiModeManager.MODE_NIGHT_YES;
                case AUTO_LIGHT:
                    return system(Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1;
                case NFC:
                    NfcAdapter nfc = NfcAdapter.getDefaultAdapter(service);
                    return nfc != null && nfc.isEnabled();
                case SYNC: return ContentResolver.getMasterSyncAutomatically();
                case BATTERY: return service.getSystemService(android.os.PowerManager.class)
                        .isPowerSaveMode();
                default: return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    void click(Item item) {
        switch (item) {
            case AIRPLANE: system(item, "airplane",
                    new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); break;
            case WIFI: system(item, "wifi", new Intent(Settings.Panel.ACTION_WIFI)); break;
            case DATA: system(item, "data",
                    new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); break;
            case BLUETOOTH: system(item, "bluetooth",
                    new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case LOCATION: system(item, "location",
                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); break;
            case DARK: system(item, "dark", new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
            case NFC: system(item, "nfc", new Intent(Settings.ACTION_NFC_SETTINGS)); break;
            case HOTSPOT: launch(new Intent().setComponent(new ComponentName(
                    "com.android.settings", "com.android.settings.TetherSettings")),
                    new Intent(Settings.ACTION_WIRELESS_SETTINGS)); break;
            case ROTATION:
                if (canWrite()) put(Settings.System.ACCELEROMETER_ROTATION,
                        isOn(item) ? 1 : 0);
                break;
            case AUTO_LIGHT:
                if (canWrite()) put(Settings.System.SCREEN_BRIGHTNESS_MODE, isOn(item) ? 0 : 1);
                break;
            case DND: toggleDnd(); break;
            case TORCH:
                if (torchId == null) { toast("Senter tidak tersedia"); break; }
                try { camera.setTorchMode(torchId, !torchOn); }
                catch (Exception e) { toast("Kamera sedang dipakai"); }
                break;
            case VIBRATE:
                try {
                    audio.setRingerMode(isOn(item) ? AudioManager.RINGER_MODE_NORMAL
                            : AudioManager.RINGER_MODE_VIBRATE);
                } catch (SecurityException e) { toast("Izin Jangan Ganggu diperlukan"); }
                break;
            case SYNC: ContentResolver.setMasterSyncAutomatically(!isOn(item)); break;
            case CALCULATOR: launch(Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)); break;
            case CAMERA: launch(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); break;
            case ALARM: launch(new Intent(AlarmClock.ACTION_SHOW_ALARMS)); break;
            case RECORD: app("com.miui.screenrecorder"); break;
            case QR: app("com.xiaomi.scanner", "com.google.ar.lens"); break;
            case BATTERY: launch(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); break;
            case SETTINGS: launch(new Intent(Settings.ACTION_SETTINGS)); break;
            case SCREENSHOT:
                service.closePanel();
                handler.postDelayed(() -> service.performGlobalAction(
                        AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT), 700);
                break;
            case NOTIFICATIONS: action(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); break;
            case RECENTS: action(AccessibilityService.GLOBAL_ACTION_RECENTS); break;
            case LOCK: action(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); break;
            case POWER: action(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG); break;
        }
    }

    /** Mode B mencoba mengubah langsung; Mode A (atau bila gagal) membuka menu sistem. */
    private void system(Item item, String target, Intent fallback) {
        if (!Shell.modeB(service)) { launch(fallback); return; }
        if (!Shell.ready()) {
            toast("Shizuku belum aktif atau belum diizinkan");
            launch(fallback);
            return;
        }
        boolean next = !isOn(item);
        Shell.run(service, shell -> shell.toggle(target, next), ok -> {
            if (!ok) {
                toast("Perintah tidak didukung di HP ini");
                launch(fallback);
            }
        });
    }

    private void action(int id) {
        service.closePanel();
        handler.postDelayed(() -> service.performGlobalAction(id), 350);
    }

    private void app(String... packages) {
        for (String name : packages) {
            Intent i = service.getPackageManager().getLaunchIntentForPackage(name);
            if (i != null) { launch(i); return; }
        }
        toast("Aplikasi tidak ditemukan di HP ini");
    }

    void launch(Intent... choices) {
        for (Intent i : choices) {
            try {
                service.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                service.closePanel();
                return;
            } catch (Exception ignored) { }
        }
        toast("Menu tidak tersedia");
    }

    boolean canWrite() {
        if (Settings.System.canWrite(service)) return true;
        toast("Buka aplikasi dan berikan izin ubah pengaturan");
        return false;
    }

    void put(String key, int value) {
        Settings.System.putInt(service.getContentResolver(), key, value);
    }

    private boolean dndOn() {
        int f = service.getSystemService(NotificationManager.class).getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL
                && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    private void toggleDnd() {
        NotificationManager nm = service.getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            toast("Buka aplikasi dan berikan izin Jangan Ganggu");
            return;
        }
        nm.setInterruptionFilter(dndOn() ? NotificationManager.INTERRUPTION_FILTER_ALL
                : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    void toast(String s) { Toast.makeText(service, s, Toast.LENGTH_SHORT).show(); }
}
