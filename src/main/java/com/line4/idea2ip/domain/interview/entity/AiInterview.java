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

@Entity
@Table(name = "ai_interviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiInterview extends BaseEntity {
    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long interviewId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<InterviewFieldState> fieldStates;

    public AiInterview(Project project, List<InterviewFieldState> fieldStates) {
        this.project = project;
        updateFields(fieldStates);
    }

    public void updateFields(List<InterviewFieldState> fieldStates) {
        this.fieldStates = List.copyOf(fieldStates);
        this.status = fieldStates.stream().anyMatch(InterviewFieldState::needsAnswer)
                ? Status.IN_PROGRESS
                : Status.COMPLETED;
    }
}
