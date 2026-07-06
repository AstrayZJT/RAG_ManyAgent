package com.astray.insightflow.eval.persistence;

import com.astray.insightflow.eval.domain.EvaluationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvaluationRecordRepository extends JpaRepository<EvaluationRecord, String> {

    Optional<EvaluationRecord> findFirstByTaskIdOrderByCreatedAtDesc(String taskId);
}
