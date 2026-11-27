package com.example.controlcenter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

/** Jendela tak terlihat yang meminta izin mikrofon dan persetujuan merekam layar. */
public class RecordActivity extends Activity {
    private int sound;

    static void open(Context c, int sound) {
        c.startActivity(new Intent(c, RecordActivity.class).putExtra("sound", sound)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        sound = getIntent().getIntExtra("sound", Recorder.SILENT);
        if (state != null) return;
        // Android juga mewajibkan izin ini untuk merekam suara dari dalam HP.
        if (sound != Recorder.SILENT && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            consent();
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Tanpa izin mikrofon, merekam tanpa suara",
                    Toast.LENGTH_LONG).show();
            sound = Recorder.SILENT;
        }
        consent();
    }

    private void consent() {
        try {
            startActivityForResult(
                    getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(), 2);
        } catch (RuntimeException e) {
            Toast.makeText(this, "HP ini tidak mendukung perekaman layar",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        if (code == 2 && result == RESULT_OK && data != null) {
            RecordService.start(this, result, data, sound);
        }
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("sound", sound);
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        sound = state.getInt("sound", sound);
    }
}
