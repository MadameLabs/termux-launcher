package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Speech-to-text through Groq's Whisper, for the Groq speech engine: each phrase the voice card
 * captures goes to {@code POST <base>/audio/transcriptions} on its own, so the card fills while
 * the user talks exactly as it does with an on-device graph. {@link TaiManager#transcribe} hands a
 * request naming {@link #MODEL_ID} here, in the app process, and nothing of the on-device runtime
 * (catalog, residency, the runtime process) is involved.
 *
 * <p>The address and the key are the remote model's ({@link TaiRemoteSettings}) with the Groq
 * preset chosen, so there is one Groq key on the phone, kept where every other remote key is.
 * Answers have the shape the on-device engines give ({@code text, model, language, languageSource})
 * or {@code {error: {code, message}, _statusCode}}; a message never carries the audio, the
 * transcript or the key.
 */
public class GroqTranscriber {

    /** The speech model id voice input sends for the Groq engine; never a catalog entry. */
    public static final String MODEL_ID = "groq/whisper-large-v3-turbo";
    /** Groq's own name for the model behind {@link #MODEL_ID}. */
    static final String GROQ_MODEL = "whisper-large-v3-turbo";
    /**
     * The longest phrase cut for Groq: Whisper reads 30 s at once, and Groq bills a request as
     * at least 10 s, so a long window costs nothing and cuts fewer sentences in half.
     */
    public static final int WINDOW_SECONDS = 30;
    /** What the voice card records: PCM16 little-endian, 16 kHz, mono ({@code VoiceActivityDetector}). */
    static final int SAMPLE_RATE = 16_000;

    private static final String BOUNDARY = "----TermuxGroqSttBoundary7Rk2";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** The read deadline's floor: a phrase's own deadline is sized for a phone, not a network. */
    private static final int MIN_READ_TIMEOUT_MS = 15_000;

    @NonNull private final String baseUrl;
    @NonNull private final String apiKey;

    @VisibleForTesting
    GroqTranscriber(@NonNull String baseUrl, @NonNull String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public static boolean isModelId(@Nullable String modelId) {
        return MODEL_ID.equals(modelId);
    }

    /** Groq chosen as the remote model, with a key stored: what the Groq speech engine needs. */
    public static boolean isReady(@NonNull Context context) {
        return fromSettings(context) != null;
    }

    @Nullable
    static GroqTranscriber fromSettings(@NonNull Context context) {
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        TaiRemotePresets.Preset preset = settings.preset();
        if (preset == null || !TaiRemotePresets.ID_GROQ.equals(preset.id)) return null;
        String key = settings.apiKey();
        if (key == null || key.trim().isEmpty()) return null;
        return new GroqTranscriber(settings.baseUrl(), key.trim());
    }

    /** The answer for a request while Groq is not set up as the remote model. */
    @NonNull
    static JSONObject notConfigured() throws JSONException {
        return error(400, "groq_not_configured",
            "Set Groq up as the remote model (On-device AI settings > Remote model) to dictate with Groq.");
    }

    /**
     * Transcribes one phrase.
     *
     * @param pcm       raw PCM16 at {@link #SAMPLE_RATE}, as the voice card writes it
     * @param language  ISO 639-1 to force, or {@code null} to let Whisper detect it
     * @param prompt    vocabulary Whisper leans towards, or {@code null}
     * @param timeoutMs the phrase's own deadline; the read waits at least {@link #MIN_READ_TIMEOUT_MS}
     */
    @NonNull
    public JSONObject transcribe(@NonNull File pcm, @Nullable String language, @Nullable String prompt,
                                 long timeoutMs) throws JSONException {
        if (!pcm.isFile() || pcm.length() == 0) {
            return error(400, "stt_audio_missing", "Audio file not found: " + pcm.getName());
        }
        boolean detect = language == null || language.trim().isEmpty();
        HttpURLConnection connection = null;
        try {
            connection = open(baseUrl + "/audio/transcriptions");
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout((int) Math.max(MIN_READ_TIMEOUT_MS, Math.min(Integer.MAX_VALUE, timeoutMs)));
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            try (DataOutputStream body = new DataOutputStream(connection.getOutputStream())) {
                writeField(body, "model", GROQ_MODEL);
                // verbose_json is the format that names the language Whisper heard.
                writeField(body, "response_format", detect ? "verbose_json" : "json");
                if (!detect) writeField(body, "language", language.trim());
                if (prompt != null && !prompt.trim().isEmpty()) writeField(body, "prompt", prompt.trim());
                writeWav(body, pcm);
                body.writeBytes("--" + BOUNDARY + "--\r\n");
                body.flush();
            }

            int status = connection.getResponseCode();
            if (status < 200 || status > 299) return httpError(status);
            JSONObject answer = new JSONObject(readBody(connection.getInputStream()));
            JSONObject result = new JSONObject();
            result.put("text", answer.optString("text", "").trim());
            result.put("model", MODEL_ID);
            if (detect) {
                String code = isoCodeFor(answer.optString("language", ""));
                if (code != null) {
                    result.put("language", code);
                    result.put(WhisperLanguageDetection.RESPONSE_SOURCE, WhisperLanguageDetection.SOURCE_DETECTED);
                }
            } else {
                result.put("language", language.trim());
                result.put(WhisperLanguageDetection.RESPONSE_SOURCE, WhisperLanguageDetection.SOURCE_FORCED);
            }
            return result;
        } catch (IOException e) {
            return error(503, "groq_unreachable", "Groq could not be reached.");
        } catch (JSONException e) {
            return error(502, "groq_bad_answer", "Groq answered with something that is not a transcript.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Seam for tests: a fake connection stands in without touching the request assembly. */
    @VisibleForTesting
    @NonNull
    HttpURLConnection open(@NonNull String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    @NonNull
    static JSONObject httpError(int status) throws JSONException {
        if (status == 401 || status == 403) return error(status, "groq_key_rejected", "Groq rejected the key.");
        if (status == 429) return error(status, "groq_rate_limited", "Groq's rate limit or quota is reached.");
        return error(status, "groq_http_" + status, "Groq could not transcribe (HTTP " + status + ").");
    }

    @NonNull
    private static JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("code", code);
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", status);
        return response;
    }

    /**
     * The ISO 639-1 code for the language Groq names: Whisper's English name ("portuguese"), or a
     * code already. {@code null} when it names nothing Java knows.
     */
    @Nullable
    static String isoCodeFor(@Nullable String language) {
        if (language == null) return null;
        String name = language.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) return null;
        if (name.length() == 2) return name;
        for (String code : Locale.getISOLanguages()) {
            if (name.equals(new Locale(code).getDisplayLanguage(Locale.ENGLISH).toLowerCase(Locale.ROOT))) return code;
        }
        return null;
    }

    /** The 44-byte header that makes {@code pcmBytes} of the card's PCM a WAV file Groq accepts. */
    @NonNull
    static byte[] wavHeader(int pcmBytes) {
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcmBytes);
        header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
            .putShort((short) 1)              // PCM
            .putShort((short) 1)              // mono
            .putInt(SAMPLE_RATE)
            .putInt(SAMPLE_RATE * 2)          // byte rate
            .putShort((short) 2)              // block align
            .putShort((short) 16);            // bits per sample
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcmBytes);
        return header.array();
    }

    private static void writeField(@NonNull DataOutputStream body, @NonNull String name, @NonNull String value) throws IOException {
        body.writeBytes("--" + BOUNDARY + "\r\n");
        body.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        body.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.writeBytes("\r\n");
    }

    private static void writeWav(@NonNull DataOutputStream body, @NonNull File pcm) throws IOException {
        body.writeBytes("--" + BOUNDARY + "\r\n");
        body.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"phrase.wav\"\r\n");
        body.writeBytes("Content-Type: audio/wav\r\n\r\n");
        body.write(wavHeader((int) pcm.length()));
        try (InputStream input = new BufferedInputStream(new FileInputStream(pcm))) {
            copy(input, body);
        }
        body.writeBytes("\r\n");
    }

    @NonNull
    private static String readBody(@Nullable InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream stream = new BufferedInputStream(input)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            copy(stream, buffer);
            return buffer.toString("UTF-8");
        }
    }

    private static void copy(@NonNull InputStream input, @NonNull OutputStream output) throws IOException {
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) > 0) output.write(chunk, 0, read);
    }
}
