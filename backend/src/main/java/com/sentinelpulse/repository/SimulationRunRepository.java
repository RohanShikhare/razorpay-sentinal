package com.sentinelpulse.repository;

import com.sentinelpulse.entity.SimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {

    Optional<SimulationRun> findFirstByActiveTrueOrderByStartedAtDesc();
}
