package com.assignment.featureflagservice.repository;

import com.assignment.featureflagservice.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

    Optional<FeatureFlag> findByFlagKey(String flagKey);
    boolean existsByFlagKey(String flagKey);
    void deleteByFlagKey(String flagKey);
}