package com.sentinelpulse.repository;

import com.sentinelpulse.entity.IncidentInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentInvestigationRepository extends JpaRepository<IncidentInvestigation, Long> {

    Optional<IncidentInvestigation> findFirstByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<IncidentInvestigation> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);
}
