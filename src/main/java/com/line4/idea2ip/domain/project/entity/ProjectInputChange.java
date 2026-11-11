package com.line4.idea2ip.domain.project.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_input_changes", uniqueConstraints = @UniqueConstraint(
        name = "uk_project_input_revision", columnNames = {"project_id", "revision"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectInputChange {
    public enum Type { FINAL_ARTWORK, MATERIAL, INTERVIEW_ANSWER, CONTRIBUTION }
    public enum Operation { ADDED, UPDATED, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false)
    private long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Operation operation;

    public ProjectInputChange(Project project, Type type, Operation operation, long resourceId) {
        this.project = project;
        this.revision = project.advanceInputRevision();
        this.type = type;
        this.operation = operation;
        this.resourceId = resourceId;
    }
}
