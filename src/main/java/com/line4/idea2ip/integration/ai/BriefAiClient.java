package com.line4.idea2ip.integration.ai;

import java.util.List;

public interface BriefAiClient {
    CandidateResult contributions(Input input);
    Draft brief(Input input);

    record Input(String projectId, String title, String snapshotVersion, List<Source> sources) {}

    record Source(String sourceId, String kind, String title, String content, String targetField,
                  String locator, Long materialId, Long artworkId, Long messageId, String resourceVersion) {}

    record CandidateResult(List<Candidate> candidates, List<String> unresolvedFields) {}

    record Candidate(String category, String statement, List<String> sourceIds) {}

    record Draft(String schemaVersion, String mode, List<Section> sections, List<Evidence> sources) {}

    record Section(String sectionKey, String title, List<String> paragraphs, List<String> sourceIds) {}

    record Evidence(String sourceId, String kind, String title, String quote, String locator,
                    String officialUrl, String edition, String effectiveFrom, String effectiveTo, String publisher,
                    String publishedOn, String retrievedOn) {
        public static Evidence from(Source source, String quote) {
            return new Evidence(source.sourceId(), source.kind(), source.title(), quote, source.locator(),
                    null, null, null, null, null, null, null);
        }
    }

    class Failure extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public Failure(String code, boolean retryable) {
            super(code);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() { return code; }
        public boolean retryable() { return retryable; }
    }
}
