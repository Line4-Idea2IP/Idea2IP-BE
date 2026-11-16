package com.line4.idea2ip.integration.ai;

import java.util.List;

/**
 * Finds where a model's quote sits in the user's own words. Models tend to fix typos while copying a quote
 * ("바궛고" becomes "바꿨고"), so an exact match alone rejects answers whose meaning was clear. A quote is accepted
 * when it matches a span of the original within a small edit distance, and that original span, not the model's
 * rewrite, is what gets stored, so every stored quote is still the user's verbatim text.
 */
final class AnswerQuotes {
    /** Shortest quote matched approximately; shorter ones must match exactly, since a few edits change them. */
    static final int MIN_FUZZY_LENGTH = 4;
    /** At most one edit for every this many characters of the quote. */
    static final int CHARACTERS_PER_EDIT = 5;
    /** Largest quote × text size searched approximately, which bounds the search table's memory. */
    private static final long MAX_FUZZY_CELLS = 2_000_000;

    private AnswerQuotes() {}

    /** The original span the quote copies, or null when no text contains it closely enough. */
    static String locate(String quote, List<String> texts) {
        if (quote == null || quote.isBlank()) return null;
        for (String text : texts) {
            if (text != null && text.contains(quote)) return quote;
        }
        String target = quote.strip();
        if (target.length() < MIN_FUZZY_LENGTH) return null;
        int allowed = target.length() / CHARACTERS_PER_EDIT;
        String best = null;
        int bestDistance = allowed + 1;
        for (String text : texts) {
            if (text == null || text.isEmpty() || (long) target.length() * text.length() > MAX_FUZZY_CELLS) continue;
            var match = closestSpan(target, text);
            if (match != null && match.distance() < bestDistance) {
                best = match.span();
                bestDistance = match.distance();
            }
        }
        return best == null || best.isBlank() ? null : best.strip();
    }

    /** Whether two quotes share words: one contains the other, or they sit on overlapping parts of one text. */
    static boolean overlaps(String quote, String other, List<String> texts) {
        if (quote.contains(other) || other.contains(quote)) return true;
        for (String text : texts) {
            int start = text.indexOf(quote);
            int otherStart = text.indexOf(other);
            if (start >= 0 && otherStart >= 0
                    && start < otherStart + other.length() && otherStart < start + quote.length()) return true;
        }
        return false;
    }

    private record Match(String span, int distance) {}

    /**
     * Approximate substring search: edit distance from the quote to the closest substring of the text,
     * computed with a free start position (first row zero), then traced back to recover that substring.
     */
    private static Match closestSpan(String quote, String text) {
        int m = quote.length();
        int n = text.length();
        int[][] cost = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) cost[i][0] = i;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int substitute = cost[i - 1][j - 1] + (quote.charAt(i - 1) == text.charAt(j - 1) ? 0 : 1);
                cost[i][j] = Math.min(substitute, Math.min(cost[i - 1][j], cost[i][j - 1]) + 1);
            }
        }
        int end = 0;
        for (int j = 1; j <= n; j++) {
            if (cost[m][j] < cost[m][end]) end = j;
        }
        int i = m;
        int j = end;
        while (i > 0 && j > 0) {
            int substitute = cost[i - 1][j - 1] + (quote.charAt(i - 1) == text.charAt(j - 1) ? 0 : 1);
            if (cost[i][j] == substitute) {
                i--;
                j--;
            } else if (cost[i][j] == cost[i - 1][j] + 1) {
                i--;
            } else {
                j--;
            }
        }
        if (j == end) return null;
        return new Match(text.substring(j, end), cost[m][end]);
    }
}
