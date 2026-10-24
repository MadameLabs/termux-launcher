package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GroqTranscriberTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void onlyTheGroqIdIsGroqs() {
        assertTrue(GroqTranscriber.isModelId(GroqTranscriber.MODEL_ID));
        assertFalse(GroqTranscriber.isModelId("whisper-acft-base"));
        assertFalse(GroqTranscriber.isModelId(""));
        assertFalse(GroqTranscriber.isModelId(null));
    }

    @Test
    public void theWavHeaderDescribesTheCardsPcm() {
        ByteBuffer header = ByteBuffer.wrap(GroqTranscriber.wavHeader(32_000)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(44, header.capacity());
        assertEquals("RIFF", ascii(header, 0));
        assertEquals(36 + 32_000, header.getInt(4));
        assertEquals("WAVE", ascii(header, 8));
        assertEquals(1, header.getShort(20));        // PCM
        assertEquals(1, header.getShort(22));        // mono
        assertEquals(16_000, header.getInt(24));
        assertEquals(32_000, header.getInt(28));     // byte rate
        assertEquals(16, header.getShort(34));
        assertEquals("data", ascii(header, 36));
        assertEquals(32_000, header.getInt(40));
    }

    @Test
    public void whisperLanguageNamesBecomeCodes() {
        assertEquals("pt", GroqTranscriber.isoCodeFor("Portuguese"));
        assertEquals("en", GroqTranscriber.isoCodeFor("english"));
        assertEquals("pt", GroqTranscriber.isoCodeFor("pt"));
        assertNull(GroqTranscriber.isoCodeFor("klingon"));
        assertNull(GroqTranscriber.isoCodeFor(""));
    }

    @Test
    public void aForcedLanguageIsSentAndReportedAsForced() throws Exception {
        FakeConnection connection = new FakeConnection(200, "{\"text\":\" olá mundo \"}");
        JSONObject result = transcriber(connection).transcribe(pcm(), "pt", "launcherctl tmux", 5_000);

        assertEquals("olá mundo", result.getString("text"));
        assertEquals(GroqTranscriber.MODEL_ID, result.getString("model"));
        assertEquals("pt", result.getString("language"));
        assertEquals(WhisperLanguageDetection.SOURCE_FORCED, result.getString(WhisperLanguageDetection.RESPONSE_SOURCE));

        String body = connection.sent();
        assertTrue(body.contains("name=\"model\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nwhisper-large-v3-turbo"));
        assertTrue(body.contains("name=\"language\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\npt"));
        assertTrue(body.contains("name=\"prompt\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nlauncherctl tmux"));
        assertTrue(body.contains("\r\n\r\njson\r\n"));
        assertTrue(body.contains("filename=\"phrase.wav\""));
        assertTrue(body.contains("RIFF"));
        assertEquals("Bearer gsk_test", connection.getRequestProperty("Authorization"));
        assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", connection.getURL().toString());
    }

    @Test
    public void withoutALanguageGroqDetectsItAndTheCardCanLockIt() throws Exception {
        FakeConnection connection = new FakeConnection(200, "{\"text\":\"bom dia\",\"language\":\"Portuguese\"}");
        JSONObject result = transcriber(connection).transcribe(pcm(), null, null, 5_000);

        assertEquals("pt", result.getString("language"));
        assertEquals(WhisperLanguageDetection.SOURCE_DETECTED, result.getString(WhisperLanguageDetection.RESPONSE_SOURCE));
        assertTrue(connection.sent().contains("verbose_json"));
        assertFalse(connection.sent().contains("name=\"language\""));
        assertFalse(connection.sent().contains("name=\"prompt\""));
    }

    @Test
    public void refusalsNameACategoryAndNeverTheAudio() throws Exception {
        assertEquals("groq_key_rejected", code(transcriber(new FakeConnection(401, "")).transcribe(pcm(), "pt", null, 5_000)));
        assertEquals("groq_rate_limited", code(transcriber(new FakeConnection(429, "")).transcribe(pcm(), "pt", null, 5_000)));
        assertEquals("groq_http_500", code(transcriber(new FakeConnection(500, "")).transcribe(pcm(), "pt", null, 5_000)));
        assertEquals("groq_bad_answer", code(transcriber(new FakeConnection(200, "not json")).transcribe(pcm(), "pt", null, 5_000)));
    }

    @Test
    public void aDeadNetworkIsUnreachable() throws Exception {
        GroqTranscriber groq = new GroqTranscriber("https://api.groq.com/openai/v1", "gsk_test") {
            @NonNull
            @Override
            HttpURLConnection open(@NonNull String url) throws IOException {
                throw new IOException("offline");
            }
        };
        JSONObject result = groq.transcribe(pcm(), "pt", null, 5_000);
        assertEquals("groq_unreachable", code(result));
        assertEquals(503, result.getInt("_statusCode"));
    }

    @Test
    public void aMissingFileIsRefusedBeforeAnyRequest() throws Exception {
        FakeConnection connection = new FakeConnection(200, "{\"text\":\"x\"}");
        JSONObject result = transcriber(connection).transcribe(new File(folder.getRoot(), "gone.pcm"), "pt", null, 5_000);
        assertEquals("stt_audio_missing", code(result));
        assertEquals("", connection.sent());
    }

    @NonNull
    private static String code(@NonNull JSONObject result) throws Exception {
        return result.getJSONObject("error").getString("code");
    }

    @NonNull
    private File pcm() throws IOException {
        File file = folder.newFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[3_200]);
        }
        return file;
    }

    @NonNull
    private static GroqTranscriber transcriber(@NonNull FakeConnection connection) {
        return new GroqTranscriber("https://api.groq.com/openai/v1", "gsk_test") {
            @NonNull
            @Override
            HttpURLConnection open(@NonNull String url) throws IOException {
                connection.target(new URL(url));
                return connection;
            }
        };
    }

    @NonNull
    private static String ascii(@NonNull ByteBuffer buffer, int at) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) bytes[i] = buffer.get(at + i);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static final class FakeConnection extends HttpURLConnection {
        private final int status;
        private final String answer;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final java.util.Map<String, String> headers = new java.util.HashMap<>();

        FakeConnection(int status, @NonNull String answer) {
            super(null);
            this.status = status;
            this.answer = answer;
        }

        void target(@NonNull URL target) {
            url = target;
        }

        @NonNull
        String sent() {
            return new String(body.toByteArray(), StandardCharsets.ISO_8859_1);
        }

        @Override public URL getURL() { return url; }
        @Override public void setRequestProperty(String key, String value) { headers.put(key, value); }
        @Override public String getRequestProperty(String key) { return headers.get(key); }
        @Override public OutputStream getOutputStream() { return body; }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(answer.getBytes(StandardCharsets.UTF_8)); }
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }
}
