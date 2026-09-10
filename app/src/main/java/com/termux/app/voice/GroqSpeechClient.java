package com.termux.app.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

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
import java.nio.charset.StandardCharsets;

/**
 * Transcription against {@code POST /openai/v1/audio/transcriptions}.
 *
 * <p>Written with {@link HttpURLConnection}, the same way every other network call in this app is
 * written, so the feature adds no dependency. The multipart body is assembled by hand because the
 * request has exactly four small fields and one file.
 */
public class GroqSpeechClient {

    static final String ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String BOUNDARY = "----TermuxVoiceBoundary7Rk2";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    /**
     * Sends the recorded audio and returns the transcript.
     *
     * @param spellingHint optional vocabulary hint; empty when there is none
     * @throws VoiceException with a category that never carries request or response content
     */
    @NonNull
    public String transcribe(@NonNull File audio, @NonNull String apiKey, @NonNull String model,
                             @NonNull String language, @NonNull String spellingHint)
            throws VoiceException {
        if (!audio.isFile() || audio.length() == 0) throw new VoiceException(VoiceFailure.NO_AUDIO);

        HttpURLConnection connection = null;
        try {
            connection = open(ENDPOINT);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            try (DataOutputStream body = new DataOutputStream(connection.getOutputStream())) {
                writeField(body, "model", model);
                writeField(body, "language", language);
                writeField(body, "response_format", "json");
                if (!spellingHint.isEmpty()) writeField(body, "prompt", spellingHint);
                writeFile(body, audio);
                body.writeBytes("--" + BOUNDARY + "--\r\n");
                body.flush();
            }

            int status = connection.getResponseCode();
            if (status < 200 || status > 299) throw new VoiceException(VoiceFailure.fromHttpStatus(status));

            String payload = readBody(connection.getInputStream());
            String text = new JSONObject(payload).optString("text", "").trim();
            if (text.isEmpty()) throw new VoiceException(VoiceFailure.EMPTY_TRANSCRIPT);
            return text;
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

    /** Seam for tests: lets a fake server stand in without touching the request assembly. */
    @VisibleForTesting
    protected HttpURLConnection open(@NonNull String endpoint) throws IOException {
        return (HttpURLConnection) new URL(endpoint).openConnection();
    }

    private static void writeField(DataOutputStream body, String name, String value) throws IOException {
        body.writeBytes("--" + BOUNDARY + "\r\n");
        body.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        body.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.writeBytes("\r\n");
    }

    private static void writeFile(DataOutputStream body, File audio) throws IOException {
        body.writeBytes("--" + BOUNDARY + "\r\n");
        body.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""
            + audio.getName() + "\"\r\n");
        body.writeBytes("Content-Type: audio/mp4\r\n\r\n");
        try (InputStream input = new BufferedInputStream(new FileInputStream(audio))) {
            copy(input, body);
        }
        body.writeBytes("\r\n");
    }

    private static String readBody(@Nullable InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream stream = new BufferedInputStream(input)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            copy(stream, buffer);
            return buffer.toString("UTF-8");
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) > 0) output.write(chunk, 0, read);
    }
}
