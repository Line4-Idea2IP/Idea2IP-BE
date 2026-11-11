package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Report extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(nullable = false)
    private int versionSequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_ready_version_id")
    private ReportVersion latestReadyVersion;

    Report(Project project) { this.project = project; }

    int nextVersionNo() { return ++versionSequence; }

    void publish(ReportVersion version) { latestReadyVersion = version; }
}
