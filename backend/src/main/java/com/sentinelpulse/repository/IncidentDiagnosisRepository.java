package com.sentinelpulse.repository;

import com.sentinelpulse.entity.IncidentDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentDiagnosisRepository extends JpaRepository<IncidentDiagnosis, Long> {

    Optional<IncidentDiagnosis> findFirstByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<IncidentDiagnosis> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);
}
