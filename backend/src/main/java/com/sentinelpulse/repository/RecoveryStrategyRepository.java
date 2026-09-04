package com.sentinelpulse.repository;

import com.sentinelpulse.entity.RecoveryStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecoveryStrategyRepository extends JpaRepository<RecoveryStrategy, Long> {

    Optional<RecoveryStrategy> findFirstByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<RecoveryStrategy> findAllByIncidentIdOrderByCreatedAtDesc(Long incidentId);
}
