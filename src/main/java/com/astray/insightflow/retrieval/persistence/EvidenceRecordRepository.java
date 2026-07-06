package com.astray.insightflow.retrieval.persistence;

import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, String> {

    List<EvidenceRecord> findByTaskIdOrderByScoreDescCreatedAtAsc(String taskId);

    void deleteByTaskId(String taskId);
}
