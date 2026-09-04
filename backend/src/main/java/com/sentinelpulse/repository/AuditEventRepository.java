package com.sentinelpulse.repository;

import com.sentinelpulse.audit.AuditEventType;
import com.sentinelpulse.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for {@link AuditEvent}.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByIncidentIdOrderByTimestampAsc(Long incidentId);

    List<AuditEvent> findByIncidentIdOrderByIdAsc(Long incidentId);

    List<AuditEvent> findByIncidentIdOrderByTimestampDesc(Long incidentId);

    List<AuditEvent> findByIncidentIdOrderByIdDesc(Long incidentId);

    List<AuditEvent> findByExecutionIdOrderByTimestampAsc(Long executionId);

    List<AuditEvent> findByExecutionIdOrderByIdAsc(Long executionId);

    List<AuditEvent> findByIncidentIdAndEventType(Long incidentId, AuditEventType eventType);

    Optional<AuditEvent> findTopByIncidentIdOrderByTimestampDesc(Long incidentId);

    Optional<AuditEvent> findTopByIncidentIdOrderByIdDesc(Long incidentId);

    long countByIncidentId(Long incidentId);

    Optional<AuditEvent> findByEventId(String eventId);
}

