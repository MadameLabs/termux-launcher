package com.termux.app.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GroqPostProcessingClientTest {

    private static JSONObject body(String system, String user, String model, float temperature)
            throws Exception {
        return new JSONObject(
            GroqPostProcessingClient.requestBody(system, user, model, temperature));
    }

    @Test
    public void theRequestCarriesTheSystemAndUserMessagesInOrder() throws Exception {
        JSONObject payload = body("regras", "<ditado>oi</ditado>", "llama-fake", 0.2f);
        JSONArray messages = payload.getJSONArray("messages");

        assertEquals(2, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("regras", messages.getJSONObject(0).getString("content"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        assertEquals("<ditado>oi</ditado>", messages.getJSONObject(1).getString("content"));
    }

    @Test
    public void theModelIsTheConfiguredOne() throws Exception {
        assertEquals("llama-fake", body("s", "u", "llama-fake", 0.2f).getString("model"));
    }

    @Test
    public void anOutOfRangeTemperatureIsClampedBeforeItLeaves() throws Exception {
        assertEquals(VoiceSettings.MAX_TEMPERATURE,
            (float) body("s", "u", "m", 4f).getDouble("temperature"), 0.0001f);
        assertEquals(VoiceSettings.MIN_TEMPERATURE,
            (float) body("s", "u", "m", -1f).getDouble("temperature"), 0.0001f);
    }

    @Test
    public void theRequestBodyNeverCarriesTheCredential() throws Exception {
        // The key travels in the Authorization header only; a logged body must stay harmless.
        String payload = GroqPostProcessingClient.requestBody("regras", "oi", "m", 0.2f);
        assertTrue(payload.indexOf("Bearer") < 0);
        assertTrue(payload.indexOf("gsk_") < 0);
    }
}
