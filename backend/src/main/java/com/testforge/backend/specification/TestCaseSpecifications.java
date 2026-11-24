package com.testforge.backend.specification;

import com.testforge.backend.model.Priority;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class TestCaseSpecifications {

    private TestCaseSpecifications() {}

    public static Specification<TestCase> filter(
            Long projectId, Priority priority, TestCaseStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("suite").get("project").get("id"), projectId));

            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("tags"), "")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}