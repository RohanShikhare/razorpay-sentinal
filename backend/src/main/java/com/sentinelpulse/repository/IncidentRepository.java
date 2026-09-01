package com.sentinelpulse.repository;

import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Page<Incident> findAllByOrderByDetectedAtDesc(Pageable pageable);

    List<Incident> findByStatusNot(IncidentStatus status);

    long countByStatusNot(IncidentStatus status);
}
