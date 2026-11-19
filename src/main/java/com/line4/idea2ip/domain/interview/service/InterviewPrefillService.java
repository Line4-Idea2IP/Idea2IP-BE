package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.interview.entity.InterviewEvidence;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;
import com.line4.idea2ip.domain.interview.entity.InterviewPrefill;
import com.line4.idea2ip.domain.interview.repository.InterviewEvidenceRepository;
import com.line4.idea2ip.domain.interview.repository.InterviewPrefillRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.repository.ProjectRepository;
import com.line4.idea2ip.integration.ai.AiInterviewClient;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Finding;
import com.line4.idea2ip.integration.ai.AiInterviewClient.InputText;
import com.line4.idea2ip.integration.ai.AiInterviewClient.ProjectInputs;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Work;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads what the project and artwork descriptions already say about each interview field, before the interview.
 * The result is stored per input revision: the readiness screen shows it and the interview starts from it, so a
 * field the user already explained is not asked. If the model cannot be reached, the plain starting states are
 * used and nothing is stored, so the next read tries again.
 */
@Slf4j
@Service
class InterviewPrefillService {
    private final ProjectRepository projects;
    private final FinalArtworkRepository artworks;
    private final InterviewPrefillRepository prefills;
    private final InterviewEvidenceRepository evidence;
    private final InterviewReadinessService readiness;
    private final AiInterviewClient ai;
    private final TransactionTemplate transaction;

    InterviewPrefillService(ProjectRepository projects, FinalArtworkRepository artworks, InterviewPrefillRepository prefills,
                            InterviewEvidenceRepository evidence, InterviewReadinessService readiness, AiInterviewClient ai,
                            PlatformTransactionManager transactionManager) {
        this.projects = projects;
        this.artworks = artworks;
        this.prefills = prefills;
        this.evidence = evidence;
        this.readiness = readiness;
        this.ai = ai;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Starting field states and which of them the descriptions completed. */
    record Prefilled(List<InterviewFieldState> fields, List<InterviewField> fromDescription) {
    }

    /**
     * @param read whether to read the descriptions when no current result is stored; false only returns what is
     *             stored for the current revision, or the plain starting states
     */
    Prefilled fields(Long projectId, boolean read) {
        Snapshot snapshot = transaction.execute(status -> {
            Project project = projects.findById(projectId).orElseThrow();
            var base = readiness.initialFields(readiness.materials(projectId));
            var current = prefills.findByProject_ProjectId(projectId)
                    .filter(prefill -> prefill.getInputRevision() == project.getInputRevision());
            return new Snapshot(project.getInputRevision(), base, current.map(InterviewPrefill::getFieldStates).orElse(null),
                    current.isPresent() && current.get().getArtworkElements() == null, inputs(project), elementTexts(project));
        });
        if (snapshot.stored() != null) {
            if (read && snapshot.elementsMissing()) recordElements(projectId, snapshot);
            return withSources(snapshot.base(), snapshot.stored());
        }
        if (!read || snapshot.inputs().texts().isEmpty() && snapshot.elementTexts().isEmpty()) {
            return new Prefilled(snapshot.base(), List.of());
        }

        List<Finding> findings;
        try {
            findings = snapshot.inputs().texts().isEmpty() ? List.of() : ai.findInInputs(snapshot.inputs());
        } catch (RuntimeException exception) {
            log.warn("Interview AI failed: phase=prefill project={} error={}", projectId, exception.getClass().getSimpleName());
            return new Prefilled(snapshot.base(), List.of());
        }
        var elements = readElements(projectId, snapshot);
        var states = merge(snapshot.base(), findings);
        try {
            transaction.executeWithoutResult(status -> {
                Project project = projects.findById(projectId).orElseThrow();
                // Descriptions changed while the model read them; the next read starts over.
                if (project.getInputRevision() != snapshot.revision()) return;
                prefills.findByProject_ProjectId(projectId).ifPresentOrElse(
                        prefill -> prefill.update(snapshot.revision(), states, elements),
                        () -> prefills.save(new InterviewPrefill(project, snapshot.revision(), states, elements)));
                evidence.deleteInputEvidence(projectId);
                findings.stream().filter(finding -> finding.status() == Status.COMPLETED)
                        .forEach(finding -> evidence.save(InterviewEvidence.fromInput(
                                project, finding.field(), finding.sourceRef(), finding.quote())));
            });
        } catch (DataIntegrityViolationException concurrent) {
            log.info("Interview prefill stored by a concurrent request: project={}", projectId);
        }
        return withSources(snapshot.base(), states);
    }

    /**
     * The final artwork for questions: its title, description and the elements read from the descriptions. The
     * elements stored last are used even after the inputs changed, since interview answers also change them.
     */
    Work work(Long projectId) {
        var artwork = artworks.findByProject_ProjectId(projectId).orElse(null);
        var elements = prefills.findByProject_ProjectId(projectId).map(InterviewPrefill::getArtworkElements).orElse(null);
        if (artwork == null && elements == null) return Work.NONE;
        return new Work(artwork == null ? null : artwork.getTitle(), artwork == null ? null : artwork.getDescription(), elements);
    }

    /** Elements are only wording for questions: a failed read leaves them empty and the questions generic. */
    private List<String> readElements(Long projectId, Snapshot snapshot) {
        if (snapshot.elementTexts().isEmpty()) return List.of();
        try {
            return ai.findElements(snapshot.elementTexts());
        } catch (RuntimeException exception) {
            log.warn("Interview AI failed: phase=elements project={} error={}", projectId, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    /** A revision whose field states were stored before artwork elements existed gets its elements once. */
    private void recordElements(Long projectId, Snapshot snapshot) {
        var elements = readElements(projectId, snapshot);
        transaction.executeWithoutResult(status -> prefills.findByProject_ProjectId(projectId)
                .filter(prefill -> prefill.getInputRevision() == snapshot.revision() && prefill.getArtworkElements() == null)
                .ifPresent(prefill -> prefill.recordArtworkElements(elements)));
    }

    private static Prefilled withSources(List<InterviewFieldState> base, List<InterviewFieldState> states) {
        var fromDescription = states.stream()
                .filter(state -> state.status() == Status.COMPLETED && base.stream()
                        .noneMatch(original -> original.field() == state.field() && original.status() == Status.COMPLETED))
                .map(InterviewFieldState::field).toList();
        return new Prefilled(states, fromDescription);
    }

    /** A completed finding settles its field; a partial one only marks an empty field as needing a closer look. */
    static List<InterviewFieldState> merge(List<InterviewFieldState> base, List<Finding> findings) {
        var result = new ArrayList<>(base);
        for (Finding finding : findings) {
            for (int index = 0; index < result.size(); index++) {
                var current = result.get(index);
                if (current.field() != finding.field()) continue;
                if (finding.status() == Status.COMPLETED) {
                    result.set(index, new InterviewFieldState(finding.field(), Status.COMPLETED, finding.quote()));
                } else if (current.status() == Status.MISSING && StringUtils.hasText(finding.gap())) {
                    result.set(index, new InterviewFieldState(finding.field(), Status.NEEDS_REVIEW, finding.gap()));
                }
            }
        }
        return List.copyOf(result);
    }

    private ProjectInputs inputs(Project project) {
        var texts = new ArrayList<InputText>();
        if (StringUtils.hasText(project.getDescription())) {
            texts.add(new InputText("project:" + project.getProjectId(), "project_description", project.getDescription()));
        }
        artworks.findByProject_ProjectId(project.getProjectId())
                .filter(artwork -> StringUtils.hasText(artwork.getDescription()))
                .ifPresent(artwork -> texts.add(new InputText("artwork:" + artwork.getArtworkId(), "artwork_description",
                        artwork.getDescription())));
        return new ProjectInputs(project.getProjectId(), List.copyOf(texts));
    }

    /** Where the artwork's elements are named: its title and description, and the project description. */
    private List<InputText> elementTexts(Project project) {
        var texts = new ArrayList<InputText>();
        artworks.findByProject_ProjectId(project.getProjectId()).ifPresent(artwork -> {
            if (StringUtils.hasText(artwork.getTitle())) {
                texts.add(new InputText("artwork:" + artwork.getArtworkId(), "artwork_title", artwork.getTitle()));
            }
            if (StringUtils.hasText(artwork.getDescription())) {
                texts.add(new InputText("artwork:" + artwork.getArtworkId(), "artwork_description", artwork.getDescription()));
            }
        });
        if (StringUtils.hasText(project.getDescription())) {
            texts.add(new InputText("project:" + project.getProjectId(), "project_description", project.getDescription()));
        }
        return List.copyOf(texts);
    }

    private record Snapshot(long revision, List<InterviewFieldState> base, List<InterviewFieldState> stored,
                            boolean elementsMissing, ProjectInputs inputs, List<InputText> elementTexts) {
    }
}
