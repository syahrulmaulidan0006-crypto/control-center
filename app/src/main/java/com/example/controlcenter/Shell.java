package com.example.controlcenter;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

/** Penghubung ke Shizuku (Mode B). */
final class Shell {
    interface Call { int run(IShell shell) throws Exception; }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final List<Runnable> WAITING = new ArrayList<>();
    private static IShell service;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IShell.Stub.asInterface(binder);
            List<Runnable> queued = new ArrayList<>(WAITING);
            WAITING.clear();
            for (Runnable r : queued) r.run();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; }
    };

    static boolean modeB(Context c) {
        return Prefs.is(c, "modeB", false);
    }

    static boolean ready() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Dipanggil dari thread utama; hasil juga dikembalikan di thread utama. */
    static void run(Context c, Call call, Consumer<Boolean> done) {
        if (!ready()) { done.accept(false); return; }
        Runnable job = () -> {
            IShell shell = service;
            WORK.execute(() -> {
                boolean ok;
                try { ok = call.run(shell) == 0; }
                catch (Exception e) { ok = false; }
                boolean result = ok;
                MAIN.post(() -> done.accept(result));
            });
        };
        if (service != null && service.asBinder().pingBinder()) { job.run(); return; }
        WAITING.add(job);
        MAIN.postDelayed(() -> { if (WAITING.remove(job)) done.accept(false); }, 5000);
        try {
            Shizuku.bindUserService(new Shizuku.UserServiceArgs(
                    new ComponentName(c.getPackageName(), ShellService.class.getName()))
                    .daemon(false).processNameSuffix("shell").version(5), CONNECTION);
        } catch (Throwable e) {
            if (WAITING.remove(job)) done.accept(false);
        }
    }
}
