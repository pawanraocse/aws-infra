package com.learning.backendservice.repository;

import com.learning.backendservice.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {
    Optional<Entry> findByKey(String key);

    boolean existsByKey(String key);
}
