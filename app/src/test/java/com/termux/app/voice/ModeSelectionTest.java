package com.termux.app.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModeSelectionTest {

    @Test
    public void rawSelectionSkipsPostProcessingEntirely() {
        assertFalse(ModeSelection.RAW.requiresPostProcessing());
        assertEquals(VoiceMode.RAW, ModeSelection.RAW.primary());
    }

    @Test
    public void correctionAndShortenReplaceEachOther() {
        ModeSelection selection = ModeSelection.RAW.withPrimary(VoiceMode.CORRECTION);
        assertEquals(VoiceMode.CORRECTION, selection.primary());

        selection = selection.withPrimary(VoiceMode.SHORTEN);
        assertEquals(VoiceMode.SHORTEN, selection.primary());
    }

    @Test
    public void pickingTheActivePrimaryClearsItBackToRaw() {
        ModeSelection selection = ModeSelection.RAW.withPrimary(VoiceMode.CORRECTION);
        assertEquals(VoiceMode.RAW, selection.withPrimary(VoiceMode.CORRECTION).primary());
    }

    @Test
    public void emojiCombinesWithRawCorrectionAndShorten() {
        assertTrue(ModeSelection.of(VoiceMode.RAW, true).emojiEnabled());
        assertTrue(ModeSelection.of(VoiceMode.CORRECTION, true).emojiEnabled());
        assertTrue(ModeSelection.of(VoiceMode.SHORTEN, true).emojiEnabled());
    }

    @Test
    public void emojiOnRawStillNeedsPostProcessing() {
        assertTrue(ModeSelection.of(VoiceMode.RAW, true).requiresPostProcessing());
    }

    @Test
    public void terminalDropsEmojiHoweverItIsSet() {
        assertFalse(ModeSelection.of(VoiceMode.TERMINAL, true).emojiEnabled());
        assertFalse(ModeSelection.of(VoiceMode.CORRECTION, true)
            .withPrimary(VoiceMode.TERMINAL).emojiEnabled());
        assertFalse(ModeSelection.of(VoiceMode.TERMINAL, false).toggleEmoji().emojiEnabled());
    }

    @Test
    public void terminalTurnsItselfOffAfterATransformation() {
        ModeSelection selection = ModeSelection.of(VoiceMode.TERMINAL, false);
        assertEquals(VoiceMode.RAW, selection.afterSuccess(true).primary());
    }

    @Test
    public void terminalSurvivesADeliveryThatFellBackToRawText() {
        ModeSelection selection = ModeSelection.of(VoiceMode.TERMINAL, false);
        assertEquals(VoiceMode.TERMINAL, selection.afterSuccess(false).primary());
    }

    @Test
    public void otherModesAreStickyAcrossSuccessfulRecordings() {
        ModeSelection selection = ModeSelection.of(VoiceMode.CORRECTION, true);
        assertEquals(selection, selection.afterSuccess(true));
    }
}
