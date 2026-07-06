package com.astray.insightflow.report.persistence;

import com.astray.insightflow.report.domain.FinalReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalReportRepository extends JpaRepository<FinalReport, String> {

    Optional<FinalReport> findByTaskId(String taskId);
}
