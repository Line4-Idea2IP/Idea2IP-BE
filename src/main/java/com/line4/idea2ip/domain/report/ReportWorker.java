package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.report.ReportModels.Operation;
import com.line4.idea2ip.domain.report.ReportModels.Stage;
import com.line4.idea2ip.domain.report.pdf.ReportPdfRenderer;
import com.line4.idea2ip.domain.report.pdf.ReportPdfStorage;
import com.line4.idea2ip.integration.ai.BriefAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
class ReportWorker {
    private final ReportService service;
    private final ReportContentAssembler assembler;
    private final BriefAiClient ai;
    private final ReportPdfRenderer renderer;
    private final ReportPdfStorage pdfs;

    @Scheduled(fixedDelayString = "${idea2ip.brief.poll-ms:2000}", initialDelayString = "${idea2ip.brief.poll-ms:2000}")
    void run() {
        for (Long id : service.runnable()) {
            var work = service.claim(id);
            if (work != null) process(work);
        }
    }

    private void process(ReportService.Work work) {
        try {
            var content = work.draft();
            if (content == null) {
                if (work.operation() == Operation.EDIT) {
                    content = assembler.edit(work.base(), work.edits());
                } else {
                    if (!service.stage(work, Stage.LINKING_SOURCES)) return;
                    var result = ai.brief(work.input().aiInput());
                    if (!service.stage(work, Stage.DRAFTING)) return;
                    content = assembler.assemble(work.input(), result, work.base(), work.baseInput());
                }
                if (!service.saveDraft(work, content)) return;
            }
            if (!ReportPdfRenderer.TEMPLATE_VERSION.equals(work.templateVersion())) {
                service.fail(work, "PDF_TEMPLATE_UNAVAILABLE", false);
                return;
            }
            var meta = new ReportPdfRenderer.Meta(work.createdAt() == null ? null : work.createdAt().toLocalDate(),
                    java.util.Set.copyOf(content.editedSectionKeys()));
            var bytes = renderer.render(work.input().aiInput().title(), work.versionNo(), meta, content.sections(), content.sources());
            var stored = pdfs.write(work.id(), work.token(), bytes);
            if (!service.publish(work, stored)) pdfs.discard(stored.key());
        } catch (BriefAiClient.Failure failure) {
            service.fail(work, failure.code(), failure.retryable());
        } catch (IOException exception) {
            log.warn("Report PDF version {} failed with {}", work.id(), exception.getClass().getSimpleName());
            service.fail(work, "PDF_RENDER_FAILED", true);
        } catch (RuntimeException exception) {
            log.warn("Report job {} failed with {}", work.id(), exception.getClass().getSimpleName());
            service.fail(work, "JOB_FAILED", true);
        }
    }
}
