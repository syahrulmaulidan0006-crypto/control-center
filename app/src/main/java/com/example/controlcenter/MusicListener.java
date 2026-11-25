package com.example.controlcenter;

import android.service.notification.NotificationListenerService;

/**
 * Hanya diperlukan agar Android mengizinkan aplikasi melihat sesi musik aktif.
 * Isi notifikasi tidak dibaca.
 */
public class MusicListener extends NotificationListenerService { }
