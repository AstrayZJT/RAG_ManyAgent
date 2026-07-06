package com.astray.insightflow.retrieval.persistence;

import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, String> {

    List<EvidenceRecord> findByTaskIdOrderByScoreDescCreatedAtAsc(String taskId);

    List<EvidenceRecord> findByTaskIdAndSourceTypeOrderByScoreDescCreatedAtAsc(String taskId, EvidenceSourceType sourceType);

    long countByTaskIdAndSourceType(String taskId, EvidenceSourceType sourceType);

    void deleteByTaskId(String taskId);

    void deleteByTaskIdAndSourceType(String taskId, EvidenceSourceType sourceType);
}
