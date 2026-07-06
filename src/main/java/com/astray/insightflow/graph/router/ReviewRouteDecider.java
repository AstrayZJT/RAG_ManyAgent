package com.astray.insightflow.graph.router;

import com.astray.insightflow.agent.reviewer.ReviewRerunTarget;
import com.astray.insightflow.config.WorkflowProperties;
import com.astray.insightflow.graph.state.ResearchState;
import org.springframework.stereotype.Component;

@Component
public class ReviewRouteDecider {

    public static final String APPROVED = "approved";
    public static final String RERUN_RETRIEVAL = "rerunRetrieval";
    public static final String RERUN_VERIFY = "rerunVerify";

    private final WorkflowProperties workflowProperties;

    public ReviewRouteDecider(WorkflowProperties workflowProperties) {
        this.workflowProperties = workflowProperties;
    }

    public String decide(ResearchState state) {
        if (state.reviewResult().isApproved() || state.loopCount() > workflowProperties.maxLoops()) {
            return APPROVED;
        }
        if (state.reviewResult().getRerunFrom() == ReviewRerunTarget.RETRIEVAL) {
            return RERUN_RETRIEVAL;
        }
        if (state.reviewResult().getRerunFrom() == ReviewRerunTarget.VERIFY) {
            return RERUN_VERIFY;
        }
        return APPROVED;
    }
}
