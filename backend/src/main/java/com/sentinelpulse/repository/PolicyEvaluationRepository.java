package com.sentinelpulse.repository;

import com.sentinelpulse.entity.PolicyEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyEvaluationRepository extends JpaRepository<PolicyEvaluation, Long> {

    Optional<PolicyEvaluation> findFirstByIncidentIdOrderByEvaluatedAtDesc(Long incidentId);

    List<PolicyEvaluation> findAllByIncidentIdOrderByEvaluatedAtDesc(Long incidentId);

    Optional<PolicyEvaluation> findFirstByStrategyIdOrderByEvaluatedAtDesc(Long strategyId);
}
