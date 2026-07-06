package com.astray.insightflow.graph.router;

import com.astray.insightflow.config.WorkflowProperties;
import com.astray.insightflow.graph.state.ResearchState;
import org.springframework.stereotype.Component;

@Component
public class VerifyRouteDecider {

    public static final String GO_WRITE = "goWrite";
    public static final String GO_RETRIEVAL = "goRetrieval";

    private final WorkflowProperties workflowProperties;

    public VerifyRouteDecider(WorkflowProperties workflowProperties) {
        this.workflowProperties = workflowProperties;
    }

    public String decide(ResearchState state) {
        if (state.verifyDecision().isReadyForWrite()) {
            return GO_WRITE;
        }
        if (state.loopCount() > workflowProperties.maxLoops()) {
            return GO_WRITE;
        }
        return state.verifyDecision().isRerunRetrieval() ? GO_RETRIEVAL : GO_WRITE;
    }
}
