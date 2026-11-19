package com.line4.idea2ip.domain.interview.entity;

import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Field states read from the project's own descriptions before the interview starts, for the input revision they
 * were read at. The readiness screen shows them, and the interview starts from them, so a field the user already
 * explained is not asked. A newer input revision means the descriptions changed and they are read again.
 */
@Entity
@Table(name = "interview_prefills",
        uniqueConstraints = @UniqueConstraint(name = "uk_interview_prefill_project", columnNames = "project_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewPrefill extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long prefillId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "input_revision", nullable = false)
    private long inputRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<InterviewFieldState> fieldStates;

    /** Parts of the artwork named in the descriptions, for questions about this artwork; null when not read yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "artwork_elements")
    private List<String> artworkElements;

    public InterviewPrefill(Project project, long inputRevision, List<InterviewFieldState> fieldStates,
                            List<String> artworkElements) {
        this.project = project;
        update(inputRevision, fieldStates, artworkElements);
    }

    public void update(long inputRevision, List<InterviewFieldState> fieldStates, List<String> artworkElements) {
        this.inputRevision = inputRevision;
        this.fieldStates = List.copyOf(fieldStates);
        this.artworkElements = artworkElements == null ? null : List.copyOf(artworkElements);
    }

    /** Elements read later for a revision whose field states were stored before elements were read. */
    public void recordArtworkElements(List<String> artworkElements) {
        this.artworkElements = List.copyOf(artworkElements);
    }
}
