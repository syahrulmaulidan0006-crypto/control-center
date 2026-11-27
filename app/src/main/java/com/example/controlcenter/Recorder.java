package com.example.controlcenter;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.view.Surface;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Merekam layar ke MP4. Gambar dan suara dikodekan sendiri agar suara dari dalam HP juga bisa
 * direkam, tidak hanya mikrofon.
 */
class Recorder {
    static final int SILENT = 0, MICROPHONE = 1, DEVICE = 2;
    private static final int RATE = 44100;

    private final MediaMuxer muxer;
    private final Object lock = new Object();
    private MediaCodec video, audio;
    private Surface surface;
    private VirtualDisplay screen;
    private AudioRecord input;
    private Thread videoThread, audioThread;
    private volatile boolean running = true;
    private int videoTrack = -1, audioTrack = -1, frames;
    private boolean muxing;

    /** Benar bila suara yang diminta ternyata tidak bisa direkam. */
    boolean soundFailed;

    Recorder(MediaProjection projection, FileDescriptor file, int width, int height, int dpi,
            int sound) throws IOException {
        muxer = new MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            prepareVideo(width, height);
            if (sound != SILENT) {
                try {
                    prepareAudio(projection, sound);
                } catch (Exception e) {
                    android.util.Log.w("ControlCenter", "suara tidak bisa direkam: " + e);
                    releaseAudio();
                    soundFailed = true;
                }
            }
            screen = projection.createVirtualDisplay("rekaman", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null);
        } catch (IOException | RuntimeException e) {
            release();
            throw e;
        }
        videoThread = new Thread(this::drainVideo, "rekam-gambar");
        videoThread.start();
        if (audio != null) {
            audioThread = new Thread(this::recordAudio, "rekam-suara");
            audioThread.start();
        }
    }

    private void prepareVideo(int width, int height) throws IOException {
        MediaFormat f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                width, height);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_BIT_RATE,
                Math.max(2_000_000, Math.min(10_000_000, width * height * 4)));
        f.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        // Layar yang diam tetap menghasilkan bingkai, agar durasi video benar.
        f.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L);
        video = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        video.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = video.createInputSurface();
        video.start();
    }

    private void prepareAudio(MediaProjection projection, int sound) throws IOException {
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        int buffer = Math.max(16384, AudioRecord.getMinBufferSize(RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2);
        AudioRecord.Builder b = new AudioRecord.Builder()
                .setAudioFormat(format).setBufferSizeInBytes(buffer);
        if (sound == DEVICE) {
            // Hanya aplikasi yang mengizinkan suaranya direkam yang akan terdengar.
            b.setAudioPlaybackCaptureConfig(
                    new AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build());
        } else {
            b.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        input = b.build();
        if (input.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IOException("perekam suara tidak siap");
        }
        MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1);
        f.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        f.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        audio = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audio.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audio.start();
        input.startRecording();
        if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            throw new IOException("mikrofon sedang dipakai");
        }
    }

    /** Berkas baru bisa ditulis setelah format semua jalur diketahui. */
    private int addTrack(MediaFormat format, boolean isVideo) {
        synchronized (lock) {
            int track = muxer.addTrack(format);
            if (isVideo) videoTrack = track; else audioTrack = track;
            if (videoTrack >= 0 && (audio == null || audioTrack >= 0)) {
                muxer.start();
                muxing = true;
                lock.notifyAll();
            }
            return track;
        }
    }

    private void write(int track, ByteBuffer data, MediaCodec.BufferInfo info) {
        synchronized (lock) {
            long until = System.currentTimeMillis() + 3000;
            while (!muxing && System.currentTimeMillis() < until) {
                try { lock.wait(200); }
                catch (InterruptedException e) { return; }
            }
            if (!muxing) return;
            muxer.writeSampleData(track, data, info);
            if (track == videoTrack) frames++;
        }
    }

    /** Mengambil hasil kodean; mengembalikan true bila sudah sampai akhir. */
    private boolean drain(MediaCodec codec, MediaCodec.BufferInfo info, long wait,
            boolean isVideo) {
        while (true) {
            int index = codec.dequeueOutputBuffer(info, wait);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                addTrack(codec.getOutputFormat(), isVideo);
                continue;
            }
            if (index < 0) continue;
            ByteBuffer data = codec.getOutputBuffer(index);
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            if (!config && info.size > 0 && data != null) {
                write(isVideo ? videoTrack : audioTrack, data, info);
            }
            codec.releaseOutputBuffer(index, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
        }
    }

    private void drainVideo() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (running) {
                if (drain(video, info, 10000, true)) return;
            }
            video.signalEndOfInputStream();
            long until = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < until) {
                if (drain(video, info, 10000, true)) return;
            }
        } catch (RuntimeException e) {
            android.util.Log.w("ControlCenter", "rekam gambar berhenti: " + e);
        }
    }

    private void recordAudio() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] chunk = new byte[4096];
        long start = 0, samples = 0;
        try {
            while (running) {
                int read = input.read(chunk, 0, chunk.length);
                if (read <= 0) continue;
                // Jam yang sama dengan bingkai gambar, lalu dihitung dari jumlah sampel.
                if (start == 0) start = System.nanoTime() / 1000 - read / 2 * 1_000_000L / RATE;
                int index = audio.dequeueInputBuffer(10000);
                if (index >= 0) {
                    ByteBuffer into = audio.getInputBuffer(index);
                    into.clear();
                    into.put(chunk, 0, read);
                    audio.queueInputBuffer(index, 0, read,
                            start + samples * 1_000_000L / RATE, 0);
                }
                samples += read / 2;
                drain(audio, info, 0, false);
            }
            int index = audio.dequeueInputBuffer(100000);
            if (index >= 0) {
                audio.queueInputBuffer(index, 0, 0, start + samples * 1_000_000L / RATE,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            long until = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < until) {
                if (drain(audio, info, 10000, false)) return;
            }
        } catch (RuntimeException e) {
            android.util.Log.w("ControlCenter", "rekam suara berhenti: " + e);
        }
    }

    /** Menyelesaikan berkas. Mengembalikan false bila tidak ada gambar yang terekam. */
    boolean stop() {
        running = false;
        for (Thread t : new Thread[]{videoThread, audioThread}) {
            if (t == null) continue;
            try { t.join(5000); }
            catch (InterruptedException ignored) { }
        }
        boolean good = false;
        synchronized (lock) {
            if (muxing) {
                try {
                    muxer.stop();
                    good = frames > 0;
                } catch (RuntimeException e) {
                    android.util.Log.w("ControlCenter", "berkas tidak bisa ditutup: " + e);
                }
                muxing = false;
            }
        }
        android.util.Log.i("ControlCenter", "rekaman selesai, bingkai=" + frames);
        release();
        return good;
    }

    private void releaseAudio() {
        if (input != null) input.release();
        if (audio != null) audio.release();
        input = null;
        audio = null;
    }

    private void release() {
        if (screen != null) screen.release();
        screen = null;
        releaseAudio();
        if (video != null) video.release();
        video = null;
        if (surface != null) surface.release();
        surface = null;
        try { muxer.release(); }
        catch (RuntimeException ignored) { }
    }
}
