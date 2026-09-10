package com.termux.app.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class VocabularyTest {

    @Test
    public void blankAndDuplicateTermsAreDropped() {
        Vocabulary vocabulary = Vocabulary.of(Arrays.asList(" TLNix ", "", "tlnix", null, "Groq"));
        assertEquals(Arrays.asList("TLNix", "Groq"), vocabulary.terms());
    }

    @Test
    public void textIsParsedByLineCommaOrSemicolon() {
        Vocabulary vocabulary = Vocabulary.parse("TLNix\nGroq, Termux; Obsidian");
        assertEquals(Arrays.asList("TLNix", "Groq", "Termux", "Obsidian"), vocabulary.terms());
    }

    @Test
    public void editableTextRoundTripsThroughParse() {
        Vocabulary vocabulary = Vocabulary.parse("TLNix\nGroq");
        assertEquals(vocabulary.terms(), Vocabulary.parse(vocabulary.asEditableText()).terms());
    }

    @Test
    public void hintStaysShortEnoughToRemainAHint() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < Vocabulary.MAX_TERMS + 20; i++) many.append("termo").append(i).append('\n');
        assertEquals(Vocabulary.MAX_TERMS, Vocabulary.parse(many.toString()).terms().size());
    }

    @Test
    public void anOverlongTermIsTruncatedInsteadOfDroppingTheWholeHint() {
        StringBuilder longTerm = new StringBuilder();
        for (int i = 0; i < Vocabulary.MAX_TERM_LENGTH + 10; i++) longTerm.append('a');
        Vocabulary vocabulary = Vocabulary.parse(longTerm.toString());
        assertEquals(Vocabulary.MAX_TERM_LENGTH, vocabulary.terms().get(0).length());
    }

    @Test
    public void emptyVocabularyProducesNoHint() {
        assertTrue(Vocabulary.parse("  ").asSpellingHint().isEmpty());
    }
}
