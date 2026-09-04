package com.sentinelpulse.repository;

import com.sentinelpulse.entity.RecoveryExecutionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecoveryExecutionAttemptRepository extends JpaRepository<RecoveryExecutionAttempt, Long> {

    List<RecoveryExecutionAttempt> findAllByExecutionIdOrderByAttemptedAtAsc(Long executionId);

    List<RecoveryExecutionAttempt> findAllByExecutionIdOrderByAttemptNumberAsc(Long executionId);
}
