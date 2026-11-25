package com.example.controlcenter;

import android.app.Application;
import rikka.shizuku.ShizukuProvider;

/** Layar pengaturan berjalan di proses terpisah dari layanan latar. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        boolean main = getPackageName().equals(getProcessName());
        ShizukuProvider.enableMultiProcessSupport(main);
        if (!main) ShizukuProvider.requestBinderForNonProviderProcess(this);
    }
}
