package com.astray.insightflow.graph.router;

import com.astray.insightflow.config.WorkflowProperties;
import com.astray.insightflow.graph.state.ResearchState;
import org.springframework.stereotype.Component;

@Component
public class InternalRouteDecider {

    public static final String SKIP_EXTERNAL = "skipExternal";
    public static final String SEARCH_EXTERNAL = "searchExternal";

    private final WorkflowProperties workflowProperties;

    public InternalRouteDecider(WorkflowProperties workflowProperties) {
        this.workflowProperties = workflowProperties;
    }

    public String decide(ResearchState state) {
        if (!state.needExternalSearch()) {
            return SKIP_EXTERNAL;
        }
        return state.internalEvidences().size() >= workflowProperties.internalEvidenceThreshold()
                ? SKIP_EXTERNAL
                : SEARCH_EXTERNAL;
    }
}
