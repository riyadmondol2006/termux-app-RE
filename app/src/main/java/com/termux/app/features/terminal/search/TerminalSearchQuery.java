package com.termux.app.features.terminal.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** What to search for in the terminal. */
public final class TerminalSearchQuery {

    @NonNull public final String text;
    public final boolean caseSensitive;
    public final boolean regex;

    public TerminalSearchQuery(@NonNull String text, boolean caseSensitive, boolean regex) {
        this.text = text;
        this.caseSensitive = caseSensitive;
        this.regex = regex;
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    /**
     * Compile the regex of the query.
     *
     * @return The pattern, or {@code null} if this is not a regex query.
     * @throws PatternSyntaxException If the regex is invalid.
     */
    @Nullable
    Pattern compilePattern() {
        if (!regex) return null;
        int flags = Pattern.MULTILINE;
        if (!caseSensitive) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(text, flags);
    }

    /** Get the error message if the query is an invalid regex, otherwise {@code null}. */
    @Nullable
    public String getError() {
        if (!regex || text.isEmpty()) return null;
        try {
            compilePattern();
            return null;
        } catch (PatternSyntaxException e) {
            return e.getDescription();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TerminalSearchQuery)) return false;
        TerminalSearchQuery that = (TerminalSearchQuery) o;
        return caseSensitive == that.caseSensitive && regex == that.regex && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, caseSensitive, regex);
    }

}
