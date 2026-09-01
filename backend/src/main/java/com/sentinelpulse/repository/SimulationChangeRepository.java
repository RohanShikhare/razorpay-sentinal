package com.sentinelpulse.repository;

import com.sentinelpulse.entity.SimulationChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationChangeRepository extends JpaRepository<SimulationChange, Long> {

    List<SimulationChange> findBySimulationRunId(Long simulationRunId);
}
