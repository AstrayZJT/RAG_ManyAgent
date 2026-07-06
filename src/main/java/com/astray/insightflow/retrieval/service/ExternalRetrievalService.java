package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.retrieval.model.Evidence;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExternalRetrievalService {

    public List<Evidence> search(String taskId, List<String> queries, boolean enabled) {
        if (!enabled) {
            return List.of();
        }
        return List.of();
    }
}
