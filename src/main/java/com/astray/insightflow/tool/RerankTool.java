package com.astray.insightflow.tool;

import com.astray.insightflow.retrieval.model.Evidence;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class RerankTool {

    @Tool("Sorts evidence by descending relevance score")
    public List<Evidence> rerank(List<Evidence> evidences) {
        List<Evidence> sorted = new ArrayList<>(evidences);
        sorted.sort(Comparator.comparingDouble(Evidence::getScore).reversed());
        return sorted;
    }
}
