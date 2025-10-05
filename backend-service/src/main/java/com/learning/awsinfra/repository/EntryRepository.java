package com.learning.awsinfra.repository;

import com.learning.awsinfra.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRepository extends JpaRepository<Entry, Long> {
    // Simple CRUD for key-value entries
}
