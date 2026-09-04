package com.sentinelpulse.repository;

import com.sentinelpulse.entity.RecoveryExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecoveryExecutionRepository extends JpaRepository<RecoveryExecution, Long> {

    Optional<RecoveryExecution> findFirstByIncidentIdOrderByStartedAtDesc(Long incidentId);

    Optional<RecoveryExecution> findFirstByStrategyIdOrderByStartedAtDesc(Long strategyId);

    List<RecoveryExecution> findAllByIncidentIdOrderByStartedAtDesc(Long incidentId);
}
