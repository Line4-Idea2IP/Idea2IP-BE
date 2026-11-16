package com.line4.idea2ip.domain.interview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps an answer's quote to the field it was asked for. The answer check often quotes the whole reply, so a reply
 * to the output-use question that also says "사과 단면은 직접 그렸고 … 명암은 손으로 조정했습니다" put those
 * sentences in the report's output-use row as well as in the human-work rows. Once the evidence search has given
 * those sentences to other fields, they are taken out of the asked field's quote.
 */
final class AnswerScope {
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?。])\\s+");

    private AnswerScope() {}

    /**
     * The longest run of the quote's sentences that no other field claimed, verbatim; the quote itself when nothing
     * was claimed from it or when every sentence was.
     */
    static String withoutClaimedSentences(String quote, List<String> claimed) {
        if (quote == null || quote.isBlank() || claimed.isEmpty()) return quote;
        var starts = new ArrayList<Integer>();
        var ends = new ArrayList<Integer>();
        var kept = new ArrayList<Boolean>();
        int from = 0;
        for (String part : SENTENCE_BREAK.split(quote)) {
            String sentence = part.strip();
            if (sentence.isEmpty()) continue;
            int at = quote.indexOf(sentence, from);
            if (at < 0) return quote;
            starts.add(at);
            ends.add(at + sentence.length());
            from = at + sentence.length();
            kept.add(claimed.stream().map(String::strip).filter(span -> !span.isEmpty())
                    .noneMatch(span -> sentence.contains(span) || span.contains(sentence)));
        }
        if (!kept.contains(false)) return quote;
        int bestStart = -1;
        int bestEnd = -1;
        for (int i = 0; i < kept.size(); i++) {
            if (!kept.get(i)) continue;
            int j = i;
            while (j + 1 < kept.size() && kept.get(j + 1)) j++;
            if (ends.get(j) - starts.get(i) > bestEnd - bestStart) {
                bestStart = starts.get(i);
                bestEnd = ends.get(j);
            }
            i = j;
        }
        return bestStart < 0 ? quote : quote.substring(bestStart, bestEnd);
    }
}
