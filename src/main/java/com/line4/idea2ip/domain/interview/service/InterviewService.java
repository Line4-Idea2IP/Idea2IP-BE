package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.interview.dto.InterviewAnswerReq;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.*;
import com.line4.idea2ip.domain.interview.entity.AiInterview;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.InterviewMessage;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
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
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;
import com.line4.idea2ip.integration.ai.AiInterviewClient.QuestionDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

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

@Slf4j
@Service
public class InterviewService {
    private final ProjectService projects;
    private final ProjectInputChanges inputChanges;
    private final AiInterviewRepository interviews;
    private final InterviewMessageRepository messages;
    private final InterviewReadinessService readiness;
    private final AiInterviewClient ai;
    private final TransactionTemplate transaction;

    public InterviewService(ProjectService projects, AiInterviewRepository interviews,
                            InterviewMessageRepository messages, InterviewReadinessService readiness,
                            AiInterviewClient ai, ProjectInputChanges inputChanges, PlatformTransactionManager transactionManager) {
        this.projects = projects;
        this.inputChanges = inputChanges;
        this.interviews = interviews;
        this.messages = messages;
        this.readiness = readiness;
        this.ai = ai;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(readOnly = true)
    public ReadinessRes getReadiness(Long userId, Long projectId) {
        projects.getOwnedProject(userId, projectId);
        var materials = readiness.materials(projectId);
        var fields = interviews.findByProject_ProjectId(projectId)
                .map(AiInterview::getFieldStates)
                .orElseGet(() -> readiness.initialFields(materials));
        return readiness.assess(projectId, materials, fields);
    }

    @Transactional(readOnly = true)
    public InterviewStateRes getInterview(Long userId, Long projectId) {
        projects.getOwnedProject(userId, projectId);
        return interviews.findByProject_ProjectId(projectId)
                .map(interview -> state(projectId, interview, latest(interview)))
                .orElseGet(() -> new InterviewStateRes(null, AiInterview.Status.NOT_STARTED,
                        readiness.initialFields(readiness.materials(projectId)), null, false));
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
        StartPlan plan = transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            if (interviews.findByProject_ProjectId(projectId).isPresent()) {
                return null;
            }
            var materials = readiness.materials(projectId);
            var fields = checkReadiness(projectId, materials);
            return new StartPlan(context(project, List.of(), materials), nextField(fields));
        });
        if (plan == null) {
            return new Started(false, advance(userId, projectId, null));
        }

        QuestionDraft question = generateQuestion(plan.context(), plan.firstField());
        Started started = transaction.execute(status -> {
            Project project = projects.getOwnedProjectForUpdate(userId, projectId);
            var existing = interviews.findByProject_ProjectId(projectId);
            if (existing.isPresent()) {
                AiInterview interview = existing.get();
                return new Started(false, state(projectId, interview, latest(interview)));
            }
            var fields = checkReadiness(projectId, readiness.materials(projectId));
            AiInterview interview = interviews.save(new AiInterview(project, fields));
            InterviewMessage first = saveQuestion(interview, 1, plan.firstField(), question);
            return new Started(true, state(projectId, interview, first));
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

        Long answerId = evaluated.answer().messageId();
        var fields = settleCovered(evaluated.context(), evaluated.answer().field(), evaluated.fields());
        InterviewField target = nextField(fields);
        String clarification = target == evaluated.answer().field()
                && StringUtils.hasText(evaluated.evaluationClarification()) ? evaluated.evaluationClarification() : null;
        QuestionDraft question = target == null ? null : generateQuestion(evaluated.context(), target, clarification);

        return transaction.execute(status -> {
            projects.getOwnedProjectForUpdate(userId, projectId);
            AiInterview interview = getStartedInterview(projectId);
            InterviewMessage last = latest(interview);
            if (!isPending(interview, last, answerId)) {
                return state(projectId, interview, last);
            }
            if (!fields.equals(interview.getFieldStates())) {
                interview.updateFields(fields);
            }
            if (target == null) {
                return state(projectId, interview, last);
            }

            var saved = saveQuestion(interview, last.getSequenceNo() + 1, target, question);
            return state(projectId, interview, saved);
        });
    }

    /**
     * Fields the earlier answers already answered are completed instead of asked: a user who said "생성 이미지는
     * 최종 작품에 안 넣었어요" while naming the tool is not asked whether the output was used. Only fields never
     * asked are settled, so a field the user is answering again keeps its own evaluation; a failed check only
     * means the field is asked as usual.
     */
    private List<InterviewFieldState> settleCovered(Context context, InterviewField answered,
                                                    List<InterviewFieldState> fields) {
        var result = fields;
        for (InterviewField target = nextField(result); target != null && target != answered && notAsked(context, target);
             target = nextField(result)) {
            InterviewFieldState settled = settleFromHistory(context, target);
            if (settled == null || settled.field() != target || settled.status() != InterviewFieldState.Status.COMPLETED) {
                break;
            }
            result = InterviewProgress.apply(result, settled, List.of());
        }
        return result;
    }

    private InterviewFieldState settleFromHistory(Context context, InterviewField field) {
        try {
            return ai.settleFromHistory(context, field);
        } catch (RuntimeException exception) {
            log.warn("Interview AI failed: phase=coverage field={} error={}", field, exception.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean notAsked(Context context, InterviewField field) {
        return context.questions().stream().noneMatch(question -> question.field() == field);
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
        return new AdvancePlan(null, context(project, history, readiness.materials(project.getProjectId())),
                toAnswer(last), List.copyOf(interview.getFieldStates()), last.getEvaluationClarification());
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

    private Context context(Project project, List<InterviewMessage> history, List<CreativeMaterial> materials) {
        var answers = history.stream().filter(message -> message.getRole() == USER).map(this::toAnswer).toList();
        var questions = history.stream().filter(message -> message.getRole() == ASSISTANT)
                .map(message -> new AskedQuestion(message.getTargetField(), message.getContent())).toList();
        List<Evidence> evidence = materials.stream()
                .flatMap(material -> material.getFiles().stream().map(file ->
                        new Evidence(InterviewReadinessService.reference(material, file).sourceId(),
                                material.getMaterialType(), material.getTitle(), material.getDescription(),
                                material.getUsedAi())))
                .toList();
        // Every answered question, so a new field can build on related answers from several turns back.
        var turns = history.stream().filter(message -> message.getRole() == USER && message.getReplyToQuestion() != null)
                .map(answer -> new PreviousTurn(answer.getReplyToQuestion().getTargetField(),
                        answer.getReplyToQuestion().getContent(), answer.getContent()))
                .toList();
        return new Context(project.getProjectId(), project.getTitle(), UUID.randomUUID().toString(),
                answers, evidence, questions, turns);
    }

    private QuestionDraft generateQuestion(Context context, InterviewField target) {
        return generateQuestion(context, target, null);
    }

    private QuestionDraft generateQuestion(Context context, InterviewField target, String clarification) {
        QuestionDraft question = callAi("question-generation", target, () -> ai.generateQuestion(context, target, clarification));
        if (question == null || !StringUtils.hasText(question.content()) || question.content().length() > 500
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

    private InterviewMessage saveQuestion(AiInterview interview, int sequence, InterviewField field, QuestionDraft question) {
        return messages.save(InterviewMessage.question(interview, sequence, field,
                question.content(), question.reason(), question.sourceIds()));
    }

    private Answer toAnswer(InterviewMessage message) {
        var question = message.getReplyToQuestion();
        return new Answer(message.getMessageId(), message.getTargetField(), message.getContent(),
                question == null ? null : question.getContent(), message.getValidatedEvidenceQuote(),
                question == null ? null : question.getReason());
    }

    private Message toMessage(InterviewMessage message) {
        Long replyTo = message.getReplyToQuestion() == null ? null : message.getReplyToQuestion().getMessageId();
        return new Message(message.getMessageId(), message.getRole(), message.getContent(), message.getSequenceNo(),
                message.getTargetField(), replyTo, message.getCreatedAt());
    }

    public record Started(boolean created, InterviewStateRes interview) {
    }

    private record StartPlan(Context context, InterviewField firstField) {
    }

    private record AdvancePlan(InterviewStateRes state, Context context, Answer answer,
                               List<InterviewFieldState> fields, String evaluationClarification) {
    }
}
