package com.echcherqaoui.jobboard.jobservice.repository;

import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;

public class JobSpecification {

    private JobSpecification() {
    }

    private static Predicate statusPredicate(JobSearchCriteria criteria, Root<Job> root, CriteriaBuilder cb) {
        return cb.equal(root.get("status"), criteria.status() != null ? criteria.status() : OPEN);
    }

    private static Optional<Predicate> keywordPredicate(JobSearchCriteria criteria, Root<Job> root, CriteriaBuilder cb) {
        if (criteria.keyword() == null || criteria.keyword().isBlank()) return Optional.empty();
        String pattern = "%" + criteria.keyword().toLowerCase() + "%";
        return Optional.of(cb.or(
              cb.like(cb.lower(root.get("title")), pattern),
              cb.like(cb.lower(root.get("description")), pattern)
        ));
    }

    private static Optional<Predicate> locationPredicate(JobSearchCriteria criteria, Root<Job> root, CriteriaBuilder cb) {
        if (criteria.location() == null || criteria.location().isBlank()) return Optional.empty();
        return Optional.of(cb.like(
              cb.lower(root.get("location")),
              "%" + criteria.location().toLowerCase() + "%"
        ));
    }

    /**
     * Builds a dynamic WHERE clause from optional filter parameters.
     * Used by the public job listing/search endpoint.
     */
    public static Specification<Job> withFilters(JobSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(statusPredicate(criteria, root, cb));
            keywordPredicate(criteria, root, cb).ifPresent(predicates::add);
            locationPredicate(criteria, root, cb).ifPresent(predicates::add);

            if (criteria.workModality() != null)
                predicates.add(cb.equal(root.get("workModality"), criteria.workModality()));
            if (criteria.jobType() != null)
                predicates.add(cb.equal(root.get("jobType"), criteria.jobType()));
            if (criteria.experienceLevel() != null)
                predicates.add(cb.equal(root.get("experienceLevel"), criteria.experienceLevel()));
            if (criteria.salaryMin() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("salaryMin"), criteria.salaryMin()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
