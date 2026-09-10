package com.termux.app.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The transformation round against {@code POST /openai/v1/chat/completions}.
 *
 * <p>Everything Groq-shaped stays here so the v2 OpenAI-compatible endpoint can arrive without the
 * keyboard, the orchestrator or the settings screen learning about it.
 */
public class GroqPostProcessingClient {

    static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    /**
     * @return the cleaned text, or {@code null} when the answer held nothing insertable — the
     *     caller then falls back to the raw transcript instead of losing the dictation
     * @throws VoiceException when the request itself failed
     */
    @Nullable
    public String transform(@NonNull String systemMessage, @NonNull String userMessage,
                            @NonNull String apiKey, @NonNull String model, float temperature)
            throws VoiceException {
        HttpURLConnection connection = null;
        try {
            connection = open(ENDPOINT);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            byte[] payload = requestBody(systemMessage, userMessage, model, temperature)
                .getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
                output.flush();
            }

            int status = connection.getResponseCode();
            if (status < 200 || status > 299) throw new VoiceException(VoiceFailure.fromHttpStatus(status));

            return GroqPromptComposer.cleanAnswer(firstChoice(readBody(connection.getInputStream())));
        } catch (VoiceException e) {
            throw e;
        } catch (IOException e) {
            throw new VoiceException(VoiceFailure.NETWORK);
        } catch (Exception e) {
            throw new VoiceException(VoiceFailure.SERVICE);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @VisibleForTesting
    static String requestBody(@NonNull String systemMessage, @NonNull String userMessage,
                              @NonNull String model, float temperature) throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemMessage));
        messages.put(new JSONObject().put("role", "user").put("content", userMessage));
        return new JSONObject()
            .put("model", model)
            .put("temperature", VoiceSettings.clampTemperature(temperature))
            .put("messages", messages)
            .toString();
    }

    @Nullable
    private static String firstChoice(@NonNull String payload) {
        try {
            JSONArray choices = new JSONObject(payload).optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            return message == null ? null : message.optString("content", null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Seam for tests: lets a fake server stand in without touching the request assembly. */
    @VisibleForTesting
    protected HttpURLConnection open(@NonNull String endpoint) throws IOException {
        return (HttpURLConnection) new URL(endpoint).openConnection();
    }

    private static String readBody(@Nullable InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream stream = new BufferedInputStream(input)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) > 0) buffer.write(chunk, 0, read);
            return buffer.toString("UTF-8");
        }
    }
}
