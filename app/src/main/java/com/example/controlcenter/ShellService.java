package com.example.controlcenter;

import java.util.concurrent.TimeUnit;

/**
 * Berjalan lewat Shizuku dengan hak shell. Hanya menerima perintah tetap di bawah,
 * bukan perintah bebas.
 */
public class ShellService extends IShell.Stub {
    public ShellService() { }

    @Override public void destroy() { System.exit(0); }

    @Override public int toggle(String target, boolean on) {
        String word = on ? "enable" : "disable";
        switch (target) {
            case "wifi": return exec("svc", "wifi", word);
            case "data": return exec("svc", "data", word);
            case "nfc": return exec("svc", "nfc", word);
            case "bluetooth":
                int code = exec("cmd", "bluetooth_manager", word);
                return code == 0 ? 0 : exec("svc", "bluetooth", word);
            case "airplane": return exec("cmd", "connectivity", "airplane-mode", word);
            case "location":
                return exec("cmd", "location", "set-location-enabled", String.valueOf(on));
            case "dark": return exec("cmd", "uimode", "night", on ? "yes" : "no");
            default: return -1;
        }
    }

    @Override public int connectWifi(String ssid, String security, String password) {
        if ("open".equals(security)) {
            return exec("cmd", "wifi", "connect-network", ssid, "open");
        }
        if (!"wpa2".equals(security) && !"wpa3".equals(security)) return -1;
        return exec("cmd", "wifi", "connect-network", ssid, security, password);
    }

    /** Argumen dikirim terpisah sehingga tidak ditafsirkan oleh shell. */
    private int exec(String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectOutput(new java.io.File("/dev/null"))
                    .redirectErrorStream(true).start();
            if (!p.waitFor(8, TimeUnit.SECONDS)) { p.destroy(); return -1; }
            return p.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }
}
