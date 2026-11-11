package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.report.ReportModels.*;
import com.line4.idea2ip.domain.report.pdf.ReportPdfRenderer;
import com.line4.idea2ip.domain.report.pdf.ReportPdfStorage;
import com.line4.idea2ip.global.entity.BaseEntity;
import com.line4.idea2ip.global.job.PersistentJob;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "report_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_version_number", columnNames = {"report_id", "version_no"}),
        @UniqueConstraint(name = "uk_report_version_request", columnNames = {"report_id", "request_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ReportVersion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Operation operation;

    private Long baseVersionId;

    @Column(nullable = false)
    private long inputRevision;

    @Column(nullable = false, length = 12)
    private String templateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private InputSnapshot inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<SectionEdit> edits;

    @JdbcTypeCode(SqlTypes.JSON)
    private Content reportContent;

    @Embedded
    private PersistentJob job = new PersistentJob();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Stage stage;

    @Column(length = 100)
    private String pdfStorageKey;

    @Column(length = 64)
    private String pdfChecksum;

    private LocalDateTime readyAt;

    ReportVersion(Report report, VersionReq req, long inputRevision, InputSnapshot snapshot) {
        this.report = report;
        this.versionNo = report.nextVersionNo();
        this.requestId = req.requestId();
        this.operation = req.operation();
        this.baseVersionId = req.baseVersionId();
        this.inputRevision = inputRevision;
        this.templateVersion = ReportPdfRenderer.TEMPLATE_VERSION;
        this.inputSnapshot = snapshot;
        this.edits = List.copyOf(req.edits());
    }

    boolean matches(VersionReq req) {
        return operation == req.operation() && java.util.Objects.equals(baseVersionId, req.baseVersionId())
                && edits.equals(req.edits());
    }

    void stage(Stage stage) { this.stage = stage; }

    void saveDraft(Content content) { this.reportContent = content; }

    void complete(ReportPdfStorage.Stored stored) {
        this.pdfStorageKey = stored.key();
        this.pdfChecksum = stored.checksum();
        this.readyAt = LocalDateTime.now();
        job.succeed();
    }

    Status status() {
        return job.getStatus() == PersistentJob.Status.SUCCEEDED ? Status.READY : Status.valueOf(job.getStatus().name());
    }
}
