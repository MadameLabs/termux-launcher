package com.termux.app.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Terms whose spelling must survive transcription — names, commands, product words.
 *
 * <p>It is a spelling reference and never content: the hint tells the model how to write a term
 * <em>if it was spoken</em>, and the prompt guard forbids introducing one that was not.
 */
public final class Vocabulary {

    /** Keeps the spelling hint short enough to stay a hint instead of dominating the request. */
    static final int MAX_TERMS = 64;
    static final int MAX_TERM_LENGTH = 64;

    private static final Vocabulary EMPTY = new Vocabulary(Collections.emptyList());

    @NonNull private final List<String> mTerms;

    private Vocabulary(@NonNull List<String> terms) {
        mTerms = Collections.unmodifiableList(terms);
    }

    public static Vocabulary empty() { return EMPTY; }

    /** Normalises a raw list: trims, drops blanks, truncates, and removes case-insensitive dupes. */
    public static Vocabulary of(@Nullable List<String> rawTerms) {
        if (rawTerms == null || rawTerms.isEmpty()) return EMPTY;
        Set<String> seen = new LinkedHashSet<>();
        List<String> terms = new ArrayList<>();
        for (String raw : rawTerms) {
            if (raw == null) continue;
            String term = raw.trim();
            if (term.isEmpty()) continue;
            if (term.length() > MAX_TERM_LENGTH) term = term.substring(0, MAX_TERM_LENGTH).trim();
            if (!seen.add(term.toLowerCase(Locale.ROOT))) continue;
            terms.add(term);
            if (terms.size() >= MAX_TERMS) break;
        }
        return terms.isEmpty() ? EMPTY : new Vocabulary(terms);
    }

    /** Parses the newline- or comma-separated text used by the settings screen. */
    public static Vocabulary parse(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return EMPTY;
        return of(java.util.Arrays.asList(text.split("[\r\n,;]+")));
    }

    @NonNull public List<String> terms() { return mTerms; }

    public boolean isEmpty() { return mTerms.isEmpty(); }

    /** One line per term, the shape the settings screen shows and stores. */
    @NonNull
    public String asEditableText() {
        return String.join("\n", mTerms);
    }

    /**
     * The spelling hint sent to the speech endpoint. Groq takes a free-form {@code prompt}; a bare
     * comma-separated glossary is the form least likely to be read as text to produce.
     */
    @NonNull
    public String asSpellingHint() {
        return isEmpty() ? "" : String.join(", ", mTerms);
    }
}
