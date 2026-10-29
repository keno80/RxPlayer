// Standalone source-launcher harness, not part of the Compose application.
// stdout contains temporary media URLs; consume it in validate_streaming.py, never log it.
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class NewPipeProbe {
    static class Network extends Downloader {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        final JsonArray requests = new JsonArray();

        @Override public Response execute(Request request) throws IOException {
            URI uri = URI.create(request.url());
            if (!Set.of("http", "https").contains(uri.getScheme())) {
                throw new IOException("unsupported scheme");
            }
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
            var ignored = Set.of("host", "connection", "content-length", "expect", "upgrade", "accept-encoding");
            request.headers().forEach((key, values) -> {
                if (!ignored.contains(key.toLowerCase(Locale.ROOT))) {
                    values.forEach(value -> builder.header(key, value));
                }
            });
            builder.header("Accept-Encoding", "identity");
            byte[] body = request.dataToSend();
            builder.method(request.httpMethod(), body == null
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            try {
                var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                byte[] bytes;
                try (var input = response.body()) {
                    bytes = input.readNBytes(8 * 1024 * 1024 + 1);
                }
                if (bytes.length > 8 * 1024 * 1024) throw new IOException("response limit");
                var observation = new JsonObject();
                observation.put("host", uri.getHost());
                observation.put("status", response.statusCode());
                requests.add(observation);
                return new Response(response.statusCode(), "", response.headers().map(),
                        new String(bytes, StandardCharsets.UTF_8), response.uri().toString());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", exception);
            }
        }
    }

    public static void main(String[] args) {
        var network = new Network();
        var report = new JsonObject();
        report.put("extractor_version", "v0.26.5");
        report.put("java_version", System.getProperty("java.version"));
        report.put("video_id", args.length == 0 ? "aqz-KE-bpKQ" : args[0]);
        try {
            NewPipe.init(network);
            var info = StreamInfo.getInfo("https://www.youtube.com/watch?v=" + report.getString("video_id"));
            report.put("duration_seconds", info.getDuration());
            var streams = new JsonArray();
            for (var audio : info.getAudioStreams()) {
                var row = new JsonObject();
                row.put("is_url", audio.isUrl());
                row.put("bitrate_kbps", audio.getAverageBitrate());
                row.put("mime", audio.getFormat() == null ? null : audio.getFormat().getMimeType());
                row.put("content", audio.getContent());
                streams.add(row);
            }
            report.put("audio", streams);
            report.put("success", !streams.isEmpty());
        } catch (Throwable failure) {
            var types = new JsonArray();
            var markers = new JsonArray();
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                types.add(cause.getClass().getSimpleName());
                String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
                for (String marker : List.of("signature", "cipher", "javascript", "403", "429", "login", "captcha", "bot")) {
                    if (message.contains(marker) && !markers.contains(marker)) markers.add(marker);
                }
            }
            report.put("success", false);
            report.put("error_types", types);
            report.put("error_markers", markers);
        }
        report.put("requests", network.requests);
        System.out.println(JsonWriter.string(report));
    }
}
