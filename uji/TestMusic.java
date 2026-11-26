package com.example.controlcenter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

/** Hanya untuk uji emulator: pemutar musik tiruan dengan sampul. */
public class TestMusic extends BroadcastReceiver {
    private static MediaSession session;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra("bening")) {
            context.getSharedPreferences("cc", Context.MODE_MULTI_PROCESS).edit()
                    .putInt("popupClear", intent.getIntExtra("bening", 50)).commit();
            return;
        }
        if (intent.hasExtra("gaya")) {
            context.getSharedPreferences("cc", Context.MODE_MULTI_PROCESS).edit()
                    .putInt("popupStyle", intent.getIntExtra("gaya", 0)).commit();
            return;
        }
        Bitmap art = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        c.drawColor(0xFFB5651D);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFE14D);
        c.drawCircle(150, 150, 90, p);
        session = new MediaSession(context.getApplicationContext(), "uji");
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Lagu Uji")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artis Uji")
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1).build());
        session.setActive(true);
    }
}
