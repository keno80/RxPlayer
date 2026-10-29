package com.keno.rxplayer.m1probe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Isolated foreground, muted technical probe. No production UI or account access. */
public class ProbeActivity extends Activity {
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36";
    private JSONObject report;
    private OkHttpClient client;
    private TextView label;
    private final JSONArray playback = new JSONArray();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        label = new TextView(this);
        label.setText("RxPlayer M1 technical probe\nRunning muted tests; no account data is accessed.");
        label.setPadding(24, 48, 24, 24);
        setContentView(label);
        int proxyPort = getIntent().getIntExtra("proxy_port", 0);
        client = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
                .proxy(proxyPort > 0 ? new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress("127.0.0.1", proxyPort)) : Proxy.NO_PROXY).build();
        new Thread(() -> runProbe(proxyPort > 0), "m1-probe").start();
    }

    private synchronized void save() throws Exception {
        File target = new File(getFilesDir(), "report.json");
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (output.size() < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
            if (count < 0) break;
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private JSONObject api(String url, String referer) throws Exception {
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).header("User-Agent", UA)
                .header("Referer", referer).build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("http_" + response.code());
            return new JSONObject(new String(readLimited(response.body().byteStream(), 4 * 1024 * 1024), StandardCharsets.UTF_8));
        }
    }

    private class Network extends Downloader {
        @Override public Response execute(Request input) throws IOException {
            okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(input.url());
            input.headers().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("accept-encoding")) values.forEach(value -> builder.addHeader(name, value));
            });
            builder.header("Accept-Encoding", "identity");
            byte[] data = input.dataToSend();
            builder.method(input.httpMethod(), data == null ? null : RequestBody.create(data));
            try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
                byte[] body = readLimited(response.body().byteStream(), 8 * 1024 * 1024 + 1);
                if (body.length > 8 * 1024 * 1024) throw new IOException("response_limit");
                return new Response(response.code(), response.message(), response.headers().toMultimap(),
                        new String(body, StandardCharsets.UTF_8), response.request().url().toString());
            }
        }
    }

    private <T> T main(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        runOnUiThread(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private void waitFor(Callable<Boolean> condition, AtomicReference<String> error) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (error.get() != null) throw new IOException("player_error");
            if (condition.call()) return;
            Thread.sleep(100);
        }
        throw new IOException("state_timeout");
    }

    private void play(String source, String id, String url, String mime, String referer) throws Exception {
        JSONObject row = new JSONObject().put("source", source).put("id", id).put("mime", mime);
        playback.put(row);
        AtomicReference<String> error = new AtomicReference<>();
        ExoPlayer player = null;
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", UA);
            headers.put("Referer", referer);
            okhttp3.Request range = new okhttp3.Request.Builder().url(url).headers(okhttp3.Headers.of(headers))
                    .header("Range", "bytes=0-65535").build();
            try (okhttp3.Response response = client.newCall(range).execute()) {
                row.put("range_status", response.code());
                row.put("range_bytes", readLimited(response.body().byteStream(), 65536).length);
                if (response.code() != 206) throw new IOException("range_failed");
            }
            player = main(() -> {
                OkHttpDataSource.Factory dataSource = new OkHttpDataSource.Factory(client).setDefaultRequestProperties(headers);
                ExoPlayer created = new ExoPlayer.Builder(this)
                        .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource)).build();
                created.setVolume(0f);
                created.addListener(new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException failure) { error.set(failure.getErrorCodeName()); }
                });
                created.setMediaItem(MediaItem.fromUri(url));
                created.prepare();
                created.play();
                return created;
            });
            final ExoPlayer active = player;
            waitFor(() -> main(() -> active.getPlaybackState() == Player.STATE_READY && active.getCurrentPosition() > 700), error);
            row.put("duration_ms", main(active::getDuration));
            row.put("audio_session_id_positive", main(() -> active.getAudioSessionId() > 0));
            row.put("decoded_mime", main(() -> active.getAudioFormat() == null ? "unknown" : active.getAudioFormat().sampleMimeType));
            main(() -> { active.pause(); return null; });
            Thread.sleep(200);
            long before = main(active::getCurrentPosition);
            Thread.sleep(900);
            long drift = Math.abs(main(active::getCurrentPosition) - before);
            row.put("pause_drift_ms", drift);
            if (drift > 150) throw new IOException("pause_drift");
            main(() -> { active.seekTo(30000); return null; });
            waitFor(() -> main(() -> Math.abs(active.getCurrentPosition() - 30000) < 750), error);
            row.put("seek_position_ms", main(active::getCurrentPosition));
            main(() -> { active.play(); return null; });
            waitFor(() -> main(() -> active.getCurrentPosition() > 30700), error);
            row.put("resume_position_ms", main(active::getCurrentPosition));
            row.put("passed", true);
        } catch (Exception failure) {
            row.put("passed", false).put("error_type", failure.getClass().getSimpleName());
            if (error.get() != null) row.put("player_error", error.get());
        } finally {
            if (player != null) {
                final ExoPlayer active = player;
                main(() -> { active.release(); return null; });
                row.put("released", true);
            }
            save();
        }
    }

    private JSONObject keystore() throws Exception {
        String alias = "rxplayer-m1-synthetic-only";
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        try {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            SecretKey key = generator.generateKey();
            byte[] plain = "synthetic-session".getBytes(StandardCharsets.UTF_8);
            Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
            encrypt.init(Cipher.ENCRYPT_MODE, key);
            byte[] ciphertext = encrypt.doFinal(plain);
            Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, encrypt.getIV()));
            boolean equal = Arrays.equals(plain, decrypt.doFinal(ciphertext));
            return new JSONObject().put("round_trip", equal).put("key_exportable", key.getEncoded() != null);
        } finally {
            if (store.containsAlias(alias)) store.deleteEntry(alias);
        }
    }

    private void runProbe(boolean bridge) {
        try {
            report = new JSONObject().put("started_at_epoch_ms", System.currentTimeMillis())
                    .put("android_api", Build.VERSION.SDK_INT).put("android_release", Build.VERSION.RELEASE)
                    .put("proxy_mode", bridge ? "usb_existing_host_proxy" : "device_direct")
                    .put("internet_permission", checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
                    .put("muted", true).put("status", "running").put("playback", playback);
            save();
            report.put("keystore", keystore());
            save();
            try {
                JSONObject view = api("https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD", "https://www.bilibili.com/");
                long cid = view.getJSONObject("data").getJSONArray("pages").getJSONObject(0).getLong("cid");
                JSONObject audio = api("https://api.bilibili.com/x/player/playurl?bvid=BV1xx411c7mD&cid=" + cid + "&fnval=16", "https://www.bilibili.com/")
                        .getJSONObject("data").getJSONObject("dash").getJSONArray("audio").getJSONObject(0);
                play("bilibili", "BV1xx411c7mD", audio.getString("baseUrl"), "audio/mp4", "https://www.bilibili.com/");
            } catch (Exception failure) { report.put("bilibili_resolve_error", failure.getClass().getSimpleName()); }
            try {
                JSONObject audio = api("https://music.163.com/api/song/enhance/player/url?ids=%5B139774%5D&br=128000", "https://music.163.com/")
                        .getJSONArray("data").getJSONObject(0);
                play("netease", "139774", audio.getString("url").replaceFirst("^http:", "https:"), "audio/mpeg", "https://music.163.com/");
            } catch (Exception failure) { report.put("netease_resolve_error", failure.getClass().getSimpleName()); }
            save();
            try {
                NewPipe.init(new Network());
                StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=aqz-KE-bpKQ");
                report.put("youtube_audio_count", info.getAudioStreams().size());
                for (String mime : new String[]{"audio/mp4", "audio/webm"}) {
                    for (var audio : info.getAudioStreams()) {
                        if (audio.isUrl() && audio.getFormat() != null && mime.equals(audio.getFormat().getMimeType())) {
                            play("youtube", "aqz-KE-bpKQ", audio.getContent(), mime, "https://www.youtube.com/");
                            break;
                        }
                    }
                }
            } catch (Throwable failure) { report.put("youtube_error", failure.getClass().getSimpleName()); }
            report.put("status", "completed").put("finished_at_epoch_ms", System.currentTimeMillis());
            save();
            runOnUiThread(() -> label.setText("RxPlayer M1 probe finished.\nResults are in the private test report.\nThis is not the product UI."));
        } catch (Exception failure) {
            try { report.put("status", "failed").put("error_type", failure.getClass().getSimpleName()); save(); }
            catch (Exception ignored) { /* ADB runner reports a missing result as failure. */ }
        }
    }
}
