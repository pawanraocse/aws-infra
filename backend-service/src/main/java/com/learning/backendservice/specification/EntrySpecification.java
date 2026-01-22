package com.learning.backendservice.specification;

import com.learning.backendservice.entity.Entry;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class EntrySpecification {
    public static Specification<Entry> filterByParams(String type, Integer minAmount, Instant createdAfter) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (type != null) {
                MapJoin<Entry, String, String> metadata = root.joinMap("metadata");
                predicate = cb.and(predicate, cb.equal(metadata.key(), "type"));
                predicate = cb.and(predicate, cb.equal(metadata.value(), type));
            }
            if (minAmount != null) {
                MapJoin<Entry, String, String> metadata = root.joinMap("metadata");
                predicate = cb.and(predicate, cb.equal(metadata.key(), "amount"));
                // Use CAST function to convert metadata.value() to integer for comparison
                Expression<Integer> amountValue = cb.function("CAST", Integer.class, metadata.value(),
                        cb.literal("integer"));
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(amountValue, minAmount));
            }
            if (createdAfter != null) {
                predicate = cb.and(predicate, cb.greaterThan(root.get("createdAt"), createdAfter));
            }
            return predicate;
        };
    }
}
