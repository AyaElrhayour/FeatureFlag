package com.assignment.featureflagservice.repository;

import com.assignment.featureflagservice.entity.AuditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditHistoryRepository extends JpaRepository<AuditHistory, Long> {

    List<AuditHistory> findByFlagKeyOrderByChangedAtDesc(String flagKey);
    boolean existsByFlagKey(String flagKey);
}