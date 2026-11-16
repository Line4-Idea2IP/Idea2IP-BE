package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.interview.dto.InterviewAnswerReq;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.*;
import com.line4.idea2ip.domain.interview.entity.AiInterview;
import com.line4.idea2ip.domain.interview.entity.InterviewEvidence;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;
import com.line4.idea2ip.domain.interview.entity.InterviewMessage;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
import com.line4.idea2ip.domain.interview.repository.InterviewEvidenceRepository;
import com.line4.idea2ip.domain.interview.repository.InterviewMessageRepository;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.domain.project.service.ProjectInputChanges;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Operation;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Type;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.integration.ai.AiInterviewClient;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Evidence;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Finding;
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;
import com.line4.idea2ip.integration.ai.AiInterviewClient.QuestionDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.line4.idea2ip.domain.interview.entity.AiInterview.Status.COMPLETED;
import static com.line4.idea2ip.domain.interview.entity.InterviewMessage.Role.ASSISTANT;
import static com.line4.idea2ip.domain.interview.entity.InterviewMessage.Role.USER;
import static com.line4.idea2ip.global.response.code.ErrorResponseCode.*;

/**
 * The interview loop. Each answer is read by the model in two ways: the field that was asked is judged on its own,
 * and the same answer is searched for evidence about fields not asked yet. Everything else is decided by code:
 * which field comes next, the question (built from templates), and how it joins the conversation. Before the
 * interview the project and artwork descriptions are read the same way ({@link InterviewPrefillService}).
 */
@Slf4j
@Service
public class InterviewService {
    private final ProjectService projects;
    private final ProjectInputChanges inputChanges;
    private final AiInterviewRepository interviews;
    private final InterviewMessageRepository messages;
    private final InterviewEvidenceRepository evidence;
    private final InterviewReadinessService readiness;
    private final InterviewPrefillService prefill;
    private final AiInterviewClient ai;
    private final TransactionTemplate transaction;

    public InterviewService(ProjectService projects, AiInterviewRepository interviews,
                            InterviewMessageRepository messages, InterviewEvidenceRepository evidence,
                            InterviewReadinessService readiness, InterviewPrefillService prefill,
                            AiInterviewClient ai, ProjectInputChanges inputChanges, PlatformTransactionManager transactionManager) {
        this.projects = projects;
        this.inputChanges = inputChanges;
        this.interviews = interviews;
        this.messages = messages;
        this.evidence = evidence;
        this.readiness = readiness;
        this.prefill = prefill;
        this.ai = ai;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Before the interview this reads the descriptions once per input revision, so it may call the model. */
    public ReadinessRes getReadiness(Long userId, Long projectId) {
        var started = transaction.execute(status -> {
            projects.getOwnedProject(userId, projectId);
            return interviews.findByProject_ProjectId(projectId)
                    .map(interview -> readiness.assess(projectId, readiness.materials(projectId), interview.getFieldStates()))
                    .orElse(null);
        });
        if (started != null) return started;
        var fields = prefill.fields(projectId, true).fields();
        return transaction.execute(status -> readiness.assess(projectId, readiness.materials(projectId), fields));
    }

    @Transactional(readOnly = true)
    public InterviewStateRes getInterview(Long userId, Long projectId) {
        projects.getOwnedProject(userId, projectId);
        return interviews.findByProject_ProjectId(projectId)
                .map(interview -> state(projectId, interview, latest(interview)))
                .orElseGet(() -> new InterviewStateRes(null, AiInterview.Status.NOT_STARTED,
                        prefill.fields(projectId, false).fields(), null, false));
    }

    @Transactional(readOnly = true)
    public MessagesRes getMessages(Long userId, Long projectId) {
        projects.getOwnedProject(userId, projectId);
        return interviews.findByProject_ProjectId(projectId)
                .map(interview -> new MessagesRes(messages
                        .findAllByInterview_InterviewIdOrderBySequenceNo(interview.getInterviewId()).stream()
                        .map(this::toMessage)
                        .toList()))
                .orElseGet(() -> new MessagesRes(List.of()));
    }

    public Started start(Long userId, Long projectId) {
        Context base = transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            if (interviews.findByProject_ProjectId(projectId).isPresent()) {
                return null;
            }
            var materials = readiness.materials(projectId);
            checkReadiness(projectId, materials);
            return context(project, List.of(), materials, List.of());
        });
        if (base == null) {
            return new Started(false, advance(userId, projectId, null));
        }

        var prefilled = prefill.fields(projectId, true);
        var fields = prefilled.fields();
        InterviewField first = nextField(fields);
        QuestionDraft question = first == null ? null : generateQuestion(withFields(base, fields), first, null);
        String leadIn = first == null ? "" : InterviewTransitions.leadIn(null, first, prefilled.fromDescription(), true);
        Started started = transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            var existing = interviews.findByProject_ProjectId(projectId);
            if (existing.isPresent()) {
                AiInterview interview = existing.get();
                return new Started(false, state(projectId, interview, latest(interview)));
            }
            checkReadiness(projectId, readiness.materials(projectId));
            AiInterview interview = interviews.save(new AiInterview(project, fields));
            if (first == null) {
                return new Started(true, state(projectId, interview, null));
            }
            InterviewMessage saved = saveQuestion(interview, 1, first, question, leadIn);
            return new Started(true, state(projectId, interview, saved));
        });
        if (started.interview().nextQuestionPending()) {
            return new Started(false, advance(userId, projectId, null));
        }
        return started;
    }

    public AnswerRes submitAnswer(Long userId, Long projectId, InterviewAnswerReq req) {
        Long answerId = transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            AiInterview interview = getStartedInterview(projectId);
            String requestId = req.requestId().toString();
            var previous = messages.findByInterview_InterviewIdAndRequestId(interview.getInterviewId(), requestId);
            if (previous.isPresent()) {
                InterviewMessage answer = previous.get();
                if (!answer.getReplyToQuestion().getMessageId().equals(req.questionId())
                        || !answer.getContent().equals(req.content())) {
                    throw new BaseException(INTERVIEW_REQUEST_CONFLICT);
                }
                return answer.getMessageId();
            }

            InterviewMessage question = messages
                    .findByInterview_InterviewIdAndMessageId(interview.getInterviewId(), req.questionId())
                    .filter(message -> message.getRole() == ASSISTANT)
                    .orElseThrow(() -> new BaseException(INTERVIEW_QUESTION_NOT_FOUND));
            InterviewMessage last = latest(interview);
            if (interview.getStatus() == COMPLETED || last == null || !last.getMessageId().equals(question.getMessageId())) {
                throw new BaseException(INTERVIEW_QUESTION_CONFLICT);
            }
            Long savedId = messages.save(InterviewMessage.answer(question, requestId, req.content())).getMessageId();
            inputChanges.record(project, Type.INTERVIEW_ANSWER, Operation.ADDED, savedId);
            return savedId;
        });
        return new AnswerRes(answerId, advance(userId, projectId, answerId));
    }

    private InterviewStateRes advance(Long userId, Long projectId, Long expectedAnswerId) {
        AdvancePlan plan = transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            AiInterview interview = getStartedInterview(projectId);
            return advancePlan(project, interview, latest(interview), expectedAnswerId);
        });
        if (plan.state() != null) {
            return plan.state();
        }

        AdvancePlan evaluated = plan.evaluationClarification() == null
                ? evaluateAndStore(userId, projectId, plan)
                : plan;
        if (evaluated.state() != null) {
            return evaluated.state();
        }

        Answer answer = evaluated.answer();
        Long answerId = answer.messageId();
        var findings = findInAnswer(evaluated.context(), answer, evaluated.fields());
        // Sentences another field took as its evidence leave the asked field's quote, so the report shows each once.
        String quote = answer.evidenceQuote();
        String narrowed = answer.field() == InterviewField.AI_TOOL ? quote
                : AnswerScope.withoutClaimedSentences(quote, findings.stream().map(Finding::quote).toList());
        boolean narrow = narrowed != null && !narrowed.equals(quote);
        var fields = narrowSummary(applyFindings(evaluated.fields(), findings), answer.field(), quote, narrow ? narrowed : quote);
        var settled = findings.stream().map(Finding::field).toList();
        InterviewField target = nextField(fields);
        String clarification = target == answer.field()
                && StringUtils.hasText(evaluated.evaluationClarification()) ? evaluated.evaluationClarification() : null;
        QuestionDraft question = target == null ? null
                : generateQuestion(withFields(evaluated.context(), fields), target, clarification);
        String leadIn = target == null ? "" : InterviewTransitions.leadIn(answer.field(), target, settled, false);

        return transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            AiInterview interview = getStartedInterview(projectId);
            InterviewMessage last = latest(interview);
            if (!isPending(interview, last, answerId)) {
                return state(projectId, interview, last);
            }
            // Saved together with the next question, which ends the pending state: retries never record twice.
            if (narrow) last.narrowEvidence(narrowed);
            recordEvidence(project, last, findings);
            if (!fields.equals(interview.getFieldStates())) {
                interview.updateFields(fields);
            }
            if (target == null) {
                return state(projectId, interview, last);
            }
            var saved = saveQuestion(interview, last.getSequenceNo() + 1, target, question, leadIn);
            return state(projectId, interview, saved);
        });
    }

    /**
     * Fields the latest answer also answered, among fields never asked: "생성 이미지는 최종 작품에 안 넣었어요"
     * said while naming the tool settles the output-use field. The model proposes, the client verifies each quote,
     * and a failed search only means the fields are asked as usual.
     */
    private List<Finding> findInAnswer(Context context, Answer answer, List<InterviewFieldState> fields) {
        var asked = context.questions().stream().map(AskedQuestion::field).collect(Collectors.toSet());
        Set<InterviewField> open = EnumSet.noneOf(InterviewField.class);
        fields.stream().filter(InterviewFieldState::needsAnswer).map(InterviewFieldState::field)
                .filter(field -> field != answer.field() && !asked.contains(field)).forEach(open::add);
        if (open.isEmpty()) return List.of();
        var claimed = new ArrayList<String>();
        for (var state : fields) {
            if (state.status() != Status.COMPLETED || state.field() == InterviewField.AI_TOOL
                    || !StringUtils.hasText(state.summary())) continue;
            // The asked field's own quote is often its whole answer; only a narrower quote marks a sentence as taken.
            if (state.field() == answer.field() && state.summary().strip().equals(answer.content().strip())) continue;
            claimed.add(state.summary().strip());
        }
        try {
            return ai.findInAnswer(context, answer, open, List.copyOf(claimed)).stream()
                    .filter(finding -> open.contains(finding.field()) && finding.status() == Status.COMPLETED
                            && answer.content().contains(finding.quote()))
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Interview AI failed: phase=findings field={} error={}", answer.field(), exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private static List<InterviewFieldState> applyFindings(List<InterviewFieldState> fields, List<Finding> findings) {
        var result = fields;
        for (Finding finding : findings) {
            result = InterviewProgress.apply(result, new InterviewFieldState(finding.field(), Status.COMPLETED, finding.quote()),
                    List.of());
        }
        return result;
    }

    /** The asked field's summary follows its narrowed quote; summaries written in other words are left as they are. */
    private static List<InterviewFieldState> narrowSummary(List<InterviewFieldState> fields, InterviewField field,
                                                           String quote, String narrowed) {
        if (quote == null || quote.equals(narrowed)) return fields;
        return fields.stream().map(state -> state.field() == field && quote.equals(state.summary())
                ? new InterviewFieldState(state.field(), state.status(), narrowed, state.closed()) : state).toList();
    }

    private void recordEvidence(Project project, InterviewMessage answer, List<Finding> findings) {
        String quote = answer.getValidatedEvidenceQuote();
        if (StringUtils.hasText(quote) && answer.getContent().contains(quote)) {
            evidence.save(InterviewEvidence.fromAnswer(project, answer.getTargetField(), answer, quote));
        }
        for (Finding finding : findings) {
            evidence.save(InterviewEvidence.fromAnswer(project, finding.field(), answer, finding.quote()));
        }
    }

    private AdvancePlan evaluateAndStore(Long userId, Long projectId, AdvancePlan plan) {
        var analysis = callAi("answer-evaluation", plan.answer().field(), () -> ai.evaluate(plan.context(), plan.answer()));
        InterviewFieldState evaluation = analysis == null ? null : analysis.state();
        if (evaluation == null || evaluation.field() != plan.answer().field() || evaluation.status() == null) {
            throw new BaseException(INTERVIEW_AI_UNAVAILABLE);
        }
        var answersForField = plan.context().answers().stream()
                .filter(answer -> answer.field() == evaluation.field()).map(Answer::content).toList();
        var fields = InterviewProgress.apply(plan.fields(), evaluation, answersForField);
        Long answerId = plan.answer().messageId();

        return transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            AiInterview interview = getStartedInterview(projectId);
            InterviewMessage last = latest(interview);
            // Retries may overlap while the model runs. Under the project lock, only the first decision is stored.
            if (isPending(interview, last, answerId) && last.getEvaluationClarification() == null) {
                String clarification = nextField(fields) == evaluation.field() ? analysis.clarification() : null;
                last.recordEvaluation(analysis.evidenceQuote(), clarification);
                interview.updateFields(fields);
            }
            return advancePlan(project, interview, last, answerId);
        });
    }

    private AdvancePlan advancePlan(Project project, AiInterview interview, InterviewMessage last, Long expectedAnswerId) {
        if (!isPending(interview, last, expectedAnswerId)) {
            return new AdvancePlan(state(project.getProjectId(), interview, last), null, null, List.of(), null);
        }

        var history = messages.findAllByInterview_InterviewIdOrderBySequenceNo(interview.getInterviewId());
        var fields = List.copyOf(interview.getFieldStates());
        return new AdvancePlan(null, context(project, history, readiness.materials(project.getProjectId()), fields),
                toAnswer(last), fields, last.getEvaluationClarification());
    }

    private List<InterviewFieldState> checkReadiness(Long projectId, List<CreativeMaterial> materials) {
        var fields = readiness.initialFields(materials);
        if (!readiness.assess(projectId, materials, fields).canStart()) {
            throw new BaseException(INTERVIEW_MATERIALS_REQUIRED);
        }
        return fields;
    }

    private InterviewStateRes state(Long projectId, AiInterview interview, InterviewMessage last) {
        Question question = null;
        boolean pending = isPending(interview, last, null);
        if (interview.getStatus() != COMPLETED && last != null && last.getRole() == ASSISTANT) {
            List<MaterialRef> refs = List.of();
            if (!last.getSourceIds().isEmpty()) {
                var currentRefs = readiness.materials(projectId).stream()
                        .flatMap(material -> material.getFiles().stream()
                                .map(file -> InterviewReadinessService.reference(material, file)))
                        .collect(Collectors.toMap(MaterialRef::sourceId, ref -> ref));
                refs = last.getSourceIds().stream().filter(currentRefs::containsKey).map(currentRefs::get).toList();
            }
            question = new Question(last.getMessageId(), last.getContent(), last.getTargetField(), last.getReason(), refs);
        }
        return new InterviewStateRes(interview.getInterviewId(), interview.getStatus(),
                interview.getFieldStates(), question, pending);
    }

    /** Questions are passed without their lead-in, so comparisons and answer reading see only what was asked. */
    private Context context(Project project, List<InterviewMessage> history, List<CreativeMaterial> materials,
                            List<InterviewFieldState> fields) {
        var answers = history.stream().filter(message -> message.getRole() == USER).map(this::toAnswer).toList();
        var questions = history.stream().filter(message -> message.getRole() == ASSISTANT)
                .map(message -> new AskedQuestion(message.getTargetField(), InterviewTransitions.body(message.getContent())))
                .toList();
        List<Evidence> evidence = materials.stream()
                .flatMap(material -> material.getFiles().stream().map(file ->
                        new Evidence(InterviewReadinessService.reference(material, file).sourceId(),
                                material.getMaterialType(), material.getTitle(), material.getDescription(),
                                material.getUsedAi())))
                .toList();
        // Every answered question, so a new field can build on related answers from several turns back.
        var turns = history.stream().filter(message -> message.getRole() == USER && message.getReplyToQuestion() != null)
                .map(answer -> new PreviousTurn(answer.getReplyToQuestion().getTargetField(),
                        InterviewTransitions.body(answer.getReplyToQuestion().getContent()), answer.getContent()))
                .toList();
        return new Context(project.getProjectId(), project.getTitle(), UUID.randomUUID().toString(),
                answers, evidence, questions, turns, fields);
    }

    private static Context withFields(Context context, List<InterviewFieldState> fields) {
        return new Context(context.projectId(), context.title(), context.snapshotVersion(), context.answers(),
                context.evidence(), context.questions(), context.turns(), List.copyOf(fields));
    }

    private QuestionDraft generateQuestion(Context context, InterviewField target, String clarification) {
        QuestionDraft question = callAi("question", target, () -> ai.generateQuestion(context, target, clarification));
        if (question == null || !StringUtils.hasText(question.content()) || question.content().length() > InterviewTransitions.QUESTION_MAX
                || (question.reason() != null && question.reason().length() > 500) || question.sourceIds() == null) {
            throw new BaseException(INTERVIEW_AI_UNAVAILABLE);
        }
        Set<String> allowedIds = context.evidence().stream().map(Evidence::sourceId).collect(Collectors.toSet());
        Set<String> returnedIds = new HashSet<>(question.sourceIds());
        if (returnedIds.size() != question.sourceIds().size() || !allowedIds.containsAll(returnedIds)) {
            throw new BaseException(INTERVIEW_AI_UNAVAILABLE);
        }
        return question;
    }

    private <T> T callAi(String phase, InterviewField field, Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException exception) {
            log.warn("Interview AI failed: phase={} field={} error={}", phase, field, exception.getClass().getSimpleName());
            throw new BaseException(INTERVIEW_AI_UNAVAILABLE);
        }
    }

    private boolean isPending(AiInterview interview, InterviewMessage last, Long expectedAnswerId) {
        return interview.getStatus() != COMPLETED && last != null && last.getRole() == USER
                && (expectedAnswerId == null || expectedAnswerId.equals(last.getMessageId()));
    }

    private InterviewField nextField(List<InterviewFieldState> fields) {
        return fields.stream().filter(InterviewFieldState::needsAnswer).map(InterviewFieldState::field)
                .findFirst().orElse(null);
    }

    private AiInterview getStartedInterview(Long projectId) {
        return interviews.findByProject_ProjectId(projectId)
                .orElseThrow(() -> new BaseException(INTERVIEW_NOT_STARTED));
    }

    private InterviewMessage latest(AiInterview interview) {
        return messages.findFirstByInterview_InterviewIdOrderBySequenceNoDesc(interview.getInterviewId()).orElse(null);
    }

    private InterviewMessage saveQuestion(AiInterview interview, int sequence, InterviewField field, QuestionDraft question,
                                          String leadIn) {
        return messages.save(InterviewMessage.question(interview, sequence, field,
                InterviewTransitions.withLeadIn(leadIn, question.content()), question.reason(), question.sourceIds()));
    }

    private Answer toAnswer(InterviewMessage message) {
        var question = message.getReplyToQuestion();
        return new Answer(message.getMessageId(), message.getTargetField(), message.getContent(),
                question == null ? null : InterviewTransitions.body(question.getContent()), message.getValidatedEvidenceQuote(),
                question == null ? null : question.getReason());
    }

    private Message toMessage(InterviewMessage message) {
        Long replyTo = message.getReplyToQuestion() == null ? null : message.getReplyToQuestion().getMessageId();
        return new Message(message.getMessageId(), message.getRole(), message.getContent(), message.getSequenceNo(),
                message.getTargetField(), replyTo, message.getCreatedAt());
    }

    public record Started(boolean created, InterviewStateRes interview) {
    }

    private record AdvancePlan(InterviewStateRes state, Context context, Answer answer,
                               List<InterviewFieldState> fields, String evaluationClarification) {
    }
}
