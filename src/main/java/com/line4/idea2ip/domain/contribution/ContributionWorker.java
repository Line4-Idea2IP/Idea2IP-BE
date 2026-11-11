package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.integration.ai.BriefAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ContributionWorker {
    private final ContributionService service;
    private final BriefAiClient ai;

    @Scheduled(fixedDelayString = "${idea2ip.brief.poll-ms:2000}", initialDelayString = "${idea2ip.brief.poll-ms:2000}")
    void run() {
        for (Long id : service.runnable()) {
            var work = service.claim(id);
            if (work == null) continue;
            try {
                service.finish(work, ai.contributions(work.input()));
            } catch (BriefAiClient.Failure failure) {
                service.fail(work, failure.code(), failure.retryable());
            } catch (RuntimeException exception) {
                log.warn("Contribution job {} failed with {}", id, exception.getClass().getSimpleName());
                service.fail(work, "JOB_FAILED", true);
            }
        }
    }
}
