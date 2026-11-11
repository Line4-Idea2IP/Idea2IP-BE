package com.line4.idea2ip.domain.project.service;

import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Operation;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Type;
import com.line4.idea2ip.domain.project.repository.ProjectInputChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectInputChanges {
    private final ProjectInputChangeRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Project lockedProject, Type type, Operation operation, long resourceId) {
        repository.save(new ProjectInputChange(lockedProject, type, operation, resourceId));
    }

    public Summary summarize(Long projectId, long from, long to) {
        var byResource = new LinkedHashMap<Resource, Change>();
        for (var event : repository
                .findByProject_ProjectIdAndRevisionGreaterThanAndRevisionLessThanEqualOrderByRevision(projectId, from, to)) {
            var key = new Resource(event.getType(), event.getResourceId());
            var change = new Change(event.getOperation(), event.getOperation());
            byResource.merge(key, change, (first, next) -> new Change(first.first(), next.last()));
        }
        return new Summary(from, to, java.util.Arrays.stream(Type.values()).map(type -> {
            var changes = byResource.entrySet().stream()
                    .filter(entry -> entry.getKey().type() == type).map(java.util.Map.Entry::getValue).toList();
            return new Counts(type,
                    changes.stream().filter(change -> change.first() == Operation.ADDED && change.last() != Operation.DELETED).count(),
                    changes.stream().filter(change -> change.first() != Operation.ADDED && change.last() != Operation.DELETED).count(),
                    changes.stream().filter(change -> change.first() != Operation.ADDED && change.last() == Operation.DELETED).count());
        }).toList());
    }

    public record Summary(long fromRevision, long toRevision, List<Counts> changes) {
        public boolean hasChanges() {
            return changes.stream().anyMatch(change -> change.added() + change.updated() + change.deleted() > 0);
        }
    }
    public record Counts(Type type, long added, long updated, long deleted) {}
    private record Resource(Type type, long id) {}
    private record Change(Operation first, Operation last) {}
}
