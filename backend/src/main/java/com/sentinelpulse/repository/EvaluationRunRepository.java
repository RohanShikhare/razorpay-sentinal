package com.sentinelpulse.repository;

import com.sentinelpulse.entity.EvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for {@link EvaluationRun}.
 */
@Repository
public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {

    Optional<EvaluationRun> findByRunId(String runId);

    Optional<EvaluationRun> findTopByOrderByStartedAtDesc();
}
